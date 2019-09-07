"""A tool to create an externally-hosted APK definition JSON from an APK.

For more information see README.md.

"""

import argparse
import base64
from distutils import spawn
import hashlib
import json
import logging
import os.path
import re
import subprocess
import sys
import tempfile
import zipfile

# Google Play Console now has a minimum version of 26.
MIN_ALLOWED_SDK_VERSION = 26

# Enable basic logging to stderr
# logging.basicConfig(level=logging.DEBUG)
logging.basicConfig(level=logging.WARNING)
_log = logging.getLogger(__name__)


class MissingPrerequisiteError(Exception):
  """An exception throw to indicate that a pre-requisit check has failed.

  See `AbstractApkParser` `check_prerequisites`.
  """


class AbstractApkParser(object):
  """Provides an abstract parser that parses an APK file.

  Takes an APK file and returns a dictionary of attributes describing that APK.
  Sub-classes should override the `parse` method.

  Attributes:
    apk_file (str): The path to the APK file being parsed.

  """

  def __init__(self, apk_file):
    """Create an APK parser with a given APK file.

    Args:
      apk_file: The path to the APK file to parse.

    """
    self.apk_file = apk_file
    return

  def check_prerequisites(self):
    """Validates all pre-requisites of the parser.

    For example, a tool expected in the system PATH that is not available.
    This does not guarantee that the parser will not fail, but provides an
    early indication of definite problems.

    Raises:
      MissingPrerequisiteError: If any problems are encountered.

    """
    if not os.path.exists(self.apk_file):
      raise MissingPrerequisiteError("APK file does not exist - "
                                     + str(self.apk_file))
    return

  def parse(self):
    """Parse the APK.  Abstract method that must be overridden.

    Returns:
      A dictionary of elements this parser has extracted from the APK, and
      their values.

    """
    raise NotImplementedError("Abstract method not overriden")


class AaptParser(AbstractApkParser):
  """Parses the APK using the AOSP "aapt" tool.

  Specifically, runs the "aapt dump badging" command, and performs basic
  parsing of the output to extract fields that we need.
  """
  PACKAGE_MATCH_REGEX = re.compile(
      r"\s*package:\s*name='(.*)'\s*"
      r"versionCode='(\d+)'\s*versionName='(.+)'\s*")
  APPLICATION_REGEX = re.compile(
      r"\s*application:\s*label='(.*)'\s*icon='(.*)'\s*")
  APPLICATION_LABEL = re.compile(r"\s*application-label:\s*'(.*)'\s*")
  SDK_VERSION_REGEX = re.compile(r"\s*sdkVersion:\s*'(.*)'\s*")
  MAX_SDK_VERSION_REGEX = re.compile(r"\s*maxSdkVersion:\s*'(.*)'\s*")
  USES_FEATURE_REGEX = re.compile(r"\s*uses-feature:\s+name='(.*)'\s*")
  # Old uses-permission format:
  #     uses-permission:'android.permission.VIBRATE'
  # New uses-permission format:
  #     uses-permission: name='android.permission.VIBRATE'
  #     uses-permission: name='android.permission.WRITE_EXTERNAL_STORAGE'
  #         maxSdkVersion='18'
  USES_PERMISSION_REGEX_OLD = re.compile(r"\s*uses-permission:\s*'(.*)'\s*")
  USES_PERMISSION_REGEX_NEW = re.compile(
      r"\s*uses-permission:\s*name='(.*?)'\s*(?:maxSdkVersion='(.*)'\s*|)")

  def __init__(self, apk_file):
    super(AaptParser, self).__init__(apk_file)
    self.aapt_exe = self.locate_aapt_exe()

  def check_prerequisites(self):
    super(AaptParser, self).check_prerequisites()
    if self.aapt_exe is None:
      raise MissingPrerequisiteError("Couldn't find the aapt binary on "
                                     "system\'s PATH.  This binary is part of "
                                     "the Android developer\'s SDK.  Please "
                                     "ensure it is available on the PATH.")

  def locate_aapt_exe(self):
    return spawn.find_executable("aapt")

  def run_aapt(self):
    return subprocess.check_output(
        "\"" + self.aapt_exe + "\" dump --values badging " + self.apk_file,
        stderr=subprocess.STDOUT,
        shell=True).decode('utf-8').splitlines()

  def parse(self):
    output = {}
    for line in self.run_aapt():
      matches = self.PACKAGE_MATCH_REGEX.match(line)
      if matches:
        _log.info("Matched package")
        output["package_name"] = matches.group(1)
        output["version_code"] = matches.group(2)
        output["version_name"] = matches.group(3)
      matches = self.SDK_VERSION_REGEX.match(line)
      if matches:
        min_sdk = int(matches.group(1), 10)
        if min_sdk < MIN_ALLOWED_SDK_VERSION:
          _log.warn("Bumping min sdk from %s to %s",
                    min_sdk, MIN_ALLOWED_SDK_VERSION)
          min_sdk = MIN_ALLOWED_SDK_VERSION
        output["minimum_sdk"] = str(min_sdk)
      matches = self.MAX_SDK_VERSION_REGEX.match(line)
      if matches:
        output["maximum_sdk"] = matches.group(1)
      matches = self.APPLICATION_LABEL.match(line)
      if matches:
        output["application_label"] = matches.group(1)
      matches = self.APPLICATION_REGEX.match(line)
      if matches:
        # In the case that the explicit "application-label" field is not found
        # in the aapt output, we grab it from the "application" field.
        # (More recent versions of aapt only provide localized versions of
        # application-label in the form "application-label-xx[-XX]".)
        if "application_label" not in output:
          output["application_label"] = matches.group(1)
        output["icon_filename"] = matches.group(2)
      matches = self.USES_FEATURE_REGEX.match(line)
      if matches:
        output.setdefault("uses_feature", []).append(matches.group(1))
      matches = self.USES_PERMISSION_REGEX_OLD.match(line)
      if matches:
        output.setdefault("uses_permission", []).append({"name": matches.group(1)})
      matches = self.USES_PERMISSION_REGEX_NEW.match(line)
      if matches:
        new_permission = {"name": matches.group(1)}
        try:
          if matches.group(2) is not None:
            new_permission.update({"maxSdkVersion": matches.group(2)})
        except IndexError:
          # No maxSdkVersion - that's OK, it's not mandatory
          pass
        output.setdefault("uses_permission", []).append(new_permission)
    return output


class FileParser(AbstractApkParser):
  """Parses properties of the APK file as a system file.

  """

  def parse(self):
    output = {}
    output["file_size"] = os.path.getsize(self.apk_file)

    with open(self.apk_file, "rb") as f:
      output["file_sha1_base64"] = base64.b64encode(
          hashlib.sha1(f.read()).digest()).decode('ascii')

    with open(self.apk_file, "rb") as f:
      output["file_sha256_base64"] = base64.b64encode(
          hashlib.sha256(f.read()).digest()).decode('ascii')

    return output


class JarFileValidationParser(AbstractApkParser):
  """Parser that validates that the APK is a valid zip-aligned JAR.

  Note:
    This parser doesn't actually return any information from the APK,
    (it returns an empty dictionary), but it produces errors if the
    APK fails validation.

  """

  def __init__(self, apk_file):
    super(JarFileValidationParser, self).__init__(apk_file)
    self.zipalign_exe = self.locate_zipalign_exe()
    self.jarsigner_exe = self.locate_jarsigner_exe()

  def locate_zipalign_exe(self):
    return spawn.find_executable("zipalign")

  def locate_jarsigner_exe(self):
    return spawn.find_executable("jarsigner")

  def check_prerequisites(self):
    super(JarFileValidationParser, self).check_prerequisites()
    if self.zipalign_exe is None:
      raise MissingPrerequisiteError("Couldn't find zipalign binary in "
                                     "system's PATH.  This binary is needed "
                                     "to validate the APK.")
    if self.jarsigner_exe is None:
      raise MissingPrerequisiteError("Couldn't find jarsigner binary in "
                                     "system's PATH.  This binary is needed "
                                     "to validate the APK.")
    return

  def parse(self):
    # Validate that the zip is correctly aligned.
    try:
      subprocess.check_call("\"" + self.zipalign_exe + "\" -c 4 " + self.apk_file,
                            shell=True)
    except subprocess.CalledProcessError as e:
      raise Exception("Error: Zip alignment is incorrect", e)

    # Validate that the jar is signed correctly.
    with open(os.devnull, "w") as dev_null:
      try:
        subprocess.check_call("\"" + self.jarsigner_exe + "\" -verify " + self.apk_file,
                              stdout=dev_null,
                              shell=True)
      except subprocess.CalledProcessError as e:
        raise Exception("Error: JAR signature doesn't validate correctly", e)

    # No new data parsed from APK, return an empty dictionary.
    return {}


class IconParser(AbstractApkParser):
  """Parses the icon from the file as base64 encoded binary.

  Attributes:
    icon_filename (str) : Filename of the icon within the APK.

  """

  def __init__(self, apk_file, icon_filename):
    super(IconParser, self).__init__(apk_file)
    self.icon_filename = icon_filename

  def check_prerequisites(self):
    super(IconParser, self).check_prerequisites()
    if self.icon_filename is None:
      raise MissingPrerequisiteError("Couldn't find icon in APK")

  def parse(self):
    output = {}
    jar_zip = zipfile.ZipFile(self.apk_file)
    icon_file_bytes = jar_zip.read(self.icon_filename)
    output["icon_base64"] = base64.b64encode(icon_file_bytes).decode('ascii')
    return output


class CertificateParser(AbstractApkParser):
  """Parses the signing certificate chain from the APK.

  """

  def __init__(self, apk_file):
    super(CertificateParser, self).__init__(apk_file)
    self.openssl_exe = self.locate_openssl_exe()

  def locate_openssl_exe(self):
    return spawn.find_executable("openssl")

  def openssl_convert_rsa_cert(self, infile_path, outfile_path):
    subprocess.check_call("\"" + self.openssl_exe + "\" pkcs7 -in "
                          + infile_path
                          + " -print_certs -inform DER -out "
                          + outfile_path, shell=True)

  def check_prerequisites(self):
    # We use a command line tool rather than the openssl library to
    # simplify install for developers not experienced with Python.
    super(CertificateParser, self).check_prerequisites()
    if self.openssl_exe is None:
      raise MissingPrerequisiteError("Couldn't find openssl commandline tool "
                                     "in system PATH.")

  def parse(self):
    output = {}
    jar_zip = zipfile.ZipFile(self.apk_file)

    # Find all the files in the JAR manifest (which includes the certificate)
    # and extract into a temporary directory.
    temp_dir = tempfile.mkdtemp()
    for ii in jar_zip.namelist():
      _log.info(ii)
    _log.info("Found " + str(len(jar_zip.namelist())) + " files in the APK")
    manifest_files = [ii for ii in jar_zip.namelist()
                      if ii.startswith("META-INF")]
    for f in manifest_files:
      _log.info("Found file in manfiest folder: " + f)
    jar_zip.extractall(path=temp_dir, members=manifest_files)

    # Look at each file to try and find the RSA certificate file (we expect
    # only one, and throw an error if there are multiple).
    with tempfile.NamedTemporaryFile(
        mode="r+b", delete=False) as temp_out_file:
      _log.info("Writing to temporary output file" + temp_out_file.name)
      for path, _, files in os.walk(temp_dir):
        for f in files:
          if not f.endswith(".RSA"):
            continue
          if output.get("certificate_base64") is not None:
            raise Exception("Multiple RSA keys - APK should only be signed "
                            "by a single jarsigner.")

          # Found the RSA certificate.  Use the openssl commandline tool to
          # extract details.
          _log.info("Found RSA file: " + os.path.join(path, f))
          self.openssl_convert_rsa_cert(os.path.join(path, f),
                                        temp_out_file.name)

          # Mark the output to indicate we have found a certificate, so that
          # even if we fail to parse it properly it is counted.
          output["certificate_base64"] = []

          # Certificates have been dumped, in order (if a cert chain existed)
          # in base64 format, which is what we need.
          certificate = None
          for ii in temp_out_file:
            ii = ii.decode('ascii').strip()
            if re.match(r".*-+BEGIN\s+CERTIFICATE-+.*", ii):
              _log.debug("Begin certificate line")
              certificate = ""
            elif re.match(r".*-+END\s+CERTIFICATE-+.*", ii):
              _log.debug("End certificate line")
              output["certificate_base64"].append(certificate)
              certificate = None
            elif certificate is not None:
              certificate += ii
            else:
              _log.debug("Skipping non-cert line: " + ii)
    return output

class DataExtractor(object):
  """Class to extract the required information from the apk and hosting url.
  """

  def __init__(self, apk, externallyhostedurl):
    self.apk = apk
    self.externallyhostedurl = externallyhostedurl

  def parse(self):
    """Parse the given apk and hosting url.

    This extracts the required information and returns a dict which needs to
    be formatted as JSON to form the file that can be uploaded in the developer
    console."""

    if not os.path.exists(self.apk):
      raise MissingPrerequisiteError("Could not find APK " + self.apk)

    # Create a list of parser that provides all the data we need for our
    # externally-hosted APK JSON.
    parsers = [AaptParser(self.apk), FileParser(self.apk),
               CertificateParser(self.apk), JarFileValidationParser(self.apk)]

    # Validate the system setup
    for parser in parsers:
      parser.check_prerequisites()

    # Parse the APK
    apk_properties = {}
    for parser in parsers:
      apk_properties.update(parser.parse())

    # Also add the icon (this relies on data parsed in the previous parser).
    icon_parser = IconParser(self.apk, apk_properties.get("icon_filename"))
    icon_parser.check_prerequisites()
    apk_properties.update(icon_parser.parse())

    # Add in the externally-hosted URL and print to stdout.
    apk_properties["externally_hosted_url"] = self.externallyhostedurl
    return apk_properties


def main():
  """Print an externally-hosted APK JSON definition to stdout.

  """
  # Define flags and help text.
  arg_parser = argparse.ArgumentParser(
      description="For a given APK, create an externally hosted APK "
      "definition file.")
  arg_parser.add_argument("--apk", dest="apk_file_name", required=True)
  arg_parser.add_argument("--externallyHostedUrl",
                          dest="externally_hosted_url", required=True)
  args = arg_parser.parse_args()

  extractor = DataExtractor(args.apk_file_name, args.externally_hosted_url)
  print(json.dumps(extractor.parse(), indent=1))
  return

if __name__ == "__main__":
  main()
