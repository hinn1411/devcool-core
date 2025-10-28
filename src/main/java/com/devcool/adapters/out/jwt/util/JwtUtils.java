package com.devcool.adapters.out.jwt.util;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public class JwtUtils {

    public static String jtiFrom(String jwtToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(jwtToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            return claims.getJWTID();
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }
}
