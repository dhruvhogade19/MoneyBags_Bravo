package com.moneybags.creditcard.config;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class ClientCredentialsTokenProvider {
    private final boolean enabled; private final String tokenUri; private final String clientId; private final String secret; private final String scope;
    private volatile CachedToken cached;
    public ClientCredentialsTokenProvider(@Value("${moneybags.security.enabled:true}") boolean enabled,
            @Value("${moneybags.security.m2m.token-uri:http://localhost:8093/oauth2/token}") String tokenUri,
            @Value("${moneybags.security.m2m.client-id:credit-card-service}") String clientId,
            @Value("${moneybags.security.m2m.client-secret:${M2M_CLIENT_SECRET:}}") String secret,
            @Value("${moneybags.security.m2m.scope:cif:service product:validate accounting:service notification:service}") String scope){this.enabled=enabled;this.tokenUri=tokenUri;this.clientId=clientId;this.secret=secret;this.scope=scope;}
    public String token(){if(!enabled)return null;if(secret.isBlank())throw new IllegalStateException("M2M_CLIENT_SECRET is required when security is enabled");CachedToken value=cached;if(valid(value))return value.value();synchronized(this){value=cached;if(valid(value))return value.value();var form=new LinkedMultiValueMap<String,String>();form.add("grant_type","client_credentials");form.add("scope",scope);TokenResponse response=RestClient.create().post().uri(tokenUri).headers(h->h.setBasicAuth(clientId,secret)).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(TokenResponse.class);if(response==null||response.access_token()==null)throw new IllegalStateException("Identity service returned no access token");cached=new CachedToken(response.access_token(),Instant.now().plusSeconds(Math.max(60,response.expires_in())));return cached.value();}}
    private boolean valid(CachedToken value){return value!=null&&value.expiresAt().isAfter(Instant.now().plusSeconds(30));}
    private record TokenResponse(String access_token,long expires_in,String token_type){} private record CachedToken(String value,Instant expiresAt){}
}
