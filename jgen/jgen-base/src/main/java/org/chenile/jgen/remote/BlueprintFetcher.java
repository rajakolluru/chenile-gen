package org.chenile.jgen.remote;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Talks to the place blueprints are published. The default implementation is {@link GitHubFetcher}. */
public interface BlueprintFetcher {

    /** The tag of the latest (non-draft, non-prerelease) release. */
    String latestVersion(BlueprintReference reference) throws IOException;

    /** The downloadable assets of one release. */
    List<Asset> assets(BlueprintReference reference) throws IOException;

    /** Downloads an asset to the given file. */
    void download(Asset asset, Path target) throws IOException;

    /**
     * The repository's source archive (a zip) at the reference's version, used for a template-only blueprint when
     * the release carries no blueprint jar.
     */
    default Asset sourceArchive(BlueprintReference reference) {
        return new Asset(reference.repo() + "-" + reference.version() + "-source.zip",
                "https://api.github.com/repos/" + reference.repository() + "/zipball/" + reference.version());
    }

    /** The catalog.json at the root of a catalog repository's default branch. */
    default byte[] catalog(String owner, String repo) throws IOException {
        throw new IOException("This fetcher cannot read catalogs");
    }

    record Asset(String name, String url) { }
}
