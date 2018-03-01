package org.amv.access.sdk.hm;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * todo: create interface and move to package with other config related class in v1.0.0
 */
@Value
@Builder
public class AccessApiContext {
    @NonNull
    private final String baseUrl;
    @NonNull
    private final String apiKey;
    @NonNull
    private final String appId;
}
