# {{jreleaserCreationStamp}}
# Custom JGen formula: JReleaser's java-binary template plus the man page and shell completions.
{{#brewRequireRelative}}
require_relative "{{.}}"
{{/brewRequireRelative}}

class {{brewFormulaName}} < Formula
  desc "{{projectDescription}}"
  homepage "{{projectLinkHomepage}}"
  url "{{distributionUrl}}"{{#brewDownloadStrategy}}, :using => {{.}}{{/brewDownloadStrategy}}
  version "{{projectVersion}}"
  sha256 "{{distributionChecksumSha256}}"
  license "{{projectLicense}}"

  {{#brewHasLivecheck}}
  livecheck do
    {{#brewLivecheck}}
    {{.}}
    {{/brewLivecheck}}
  end
  {{/brewHasLivecheck}}
  {{#brewDependencies}}
  depends_on {{.}}
  {{/brewDependencies}}

  def install
    libexec.install Dir["*"]
    bin.install_symlink "#{libexec}/bin/{{distributionExecutableUnix}}" => "{{distributionExecutableName}}"
    man1.install libexec/"man/man1/jgen.1"
    bash_completion.install libexec/"completions/jgen.bash" => "jgen"
    zsh_completion.install libexec/"completions/_jgen"
    fish_completion.install libexec/"completions/jgen.fish"
  end

  test do
    output = shell_output("#{bin}/{{distributionExecutableName}} --version")
    assert_match "{{projectVersion}}", output
  end
end
