package com.yucli.mcp.auth;

public interface TokenProvider {
    String getAccessToken();
    boolean isTokenValid();
    void refreshToken();
}
