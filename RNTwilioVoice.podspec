require 'json'

package = JSON.load(File.read(File.expand_path("./package.json", __dir__)))

Pod::Spec.new do |s|
  s.name         = "RNTwilioVoice"
  s.version      = package['version']
  s.summary      = package['description']
  s.author       = package['contributors'][0]['name']
  s.homepage     = package['homepage']
  s.license      = package['license']
  s.platform     = :ios, "11.0"
  s.requires_arc   = true

  s.source_files   = 'ios/RNTwilioVoice/*.{h,m}'
  s.source         = { git: 'https://github.com/hoxfon/react-native-twilio-programmable-voice', tag: s.version }

  s.dependency 'React-Core'
  s.dependency 'TwilioVoice', '~> 5.2.0'
  s.xcconfig = { 'FRAMEWORK_SEARCH_PATHS' => '${PODS_ROOT}/TwilioVoice/Build/iOS' }
  s.frameworks   = 'TwilioVoice'
  s.preserve_paths = 'LICENSE', 'README.md', 'package.json', 'index.js'

end
