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

    record Asset(String name, String url) { }
}
