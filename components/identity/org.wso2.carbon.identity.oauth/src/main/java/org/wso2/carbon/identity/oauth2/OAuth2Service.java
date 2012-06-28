/*
*Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*WSO2 Inc. licenses this file to you under the Apache License,
*Version 2.0 (the "License"); you may not use this file except
*in compliance with the License.
*You may obtain a copy of the License at
*
*http://www.apache.org/licenses/LICENSE-2.0
*
*Unless required by applicable law or agreed to in writing,
*software distributed under the License is distributed on an
*"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*KIND, either express or implied.  See the License for the
*specific language governing permissions and limitations
*under the License.
*/

package org.wso2.carbon.identity.oauth2;

import com.google.gdata.client.authn.oauth.OAuthException;
import org.apache.amber.oauth2.as.issuer.MD5Generator;
import org.apache.amber.oauth2.as.issuer.OAuthIssuerImpl;
import org.apache.amber.oauth2.common.error.OAuthError;
import org.apache.amber.oauth2.common.message.types.GrantType;
import org.apache.amber.oauth2.common.message.types.ResponseType;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.AbstractAdmin;
import org.wso2.carbon.identity.core.model.OAuthAppDO;
import org.wso2.carbon.identity.oauth.IdentityOAuthAdminException;
import org.wso2.carbon.identity.oauth.authz.OAuthAuthorizationCallback;
import org.wso2.carbon.identity.oauth.authz.OAuthAuthorizationHandler;
import org.wso2.carbon.identity.oauth.common.OAuth2ErrorCodes;
import org.wso2.carbon.identity.oauth.dao.OAuthAppDAO;
import org.wso2.carbon.identity.oauth2.dao.TokenMgtDAO;
import org.wso2.carbon.identity.oauth2.dto.*;
import org.wso2.carbon.identity.oauth2.util.OAuth2Constants;
import org.wso2.carbon.identity.oauth2.util.OAuth2Util;

import java.sql.Timestamp;
import java.util.Date;

/**
 * OAuth2 Service which is used to issue authorization codes or access tokens upon authorizing by the
 * user and issue/validate access tokens.
 */
@SuppressWarnings("unused")
public class OAuth2Service extends AbstractAdmin {

    private static Log log = LogFactory.getLog(OAuth2Service.class);

    /**
     * Process the authorization request and issue an authorization code or access token depending
     * on the Response Type available in the request.
     *
     * @param authorizeDTO <code>OAuth2AuthorizeReqDTO</code> containing information about the authorization
     *                     request.
     * @return <code>OAuth2AuthorizeRespDTO</code> instance containing the access token/authorization code
     *         or an error code.
     */
    public OAuth2AuthorizeRespDTO authorize(OAuth2AuthorizeReqDTO authorizeDTO) {

        if (log.isDebugEnabled()) {
            log.debug("Authorization Request received for user : " + authorizeDTO.getUsername() +
                    ", Client ID : " + authorizeDTO.getConsumerKey() +
                    ", Authorization Response Type : " + authorizeDTO.getResponseType() +
                    ", Requested callback URI : " + authorizeDTO.getCallbackUrl() +
                    ", Requested Scope : " + OAuth2Util.buildScopeString(authorizeDTO.getScopes()));
        }

        OAuth2AuthorizeRespDTO respDTO = new OAuth2AuthorizeRespDTO();
        boolean isAuthenticated;

        // authenticate user
        try {
            isAuthenticated = OAuth2Util.authenticateUser(authorizeDTO.getUsername(),
                    authorizeDTO.getPassword());
        } catch (OAuthException e) {
            log.error("Error occurred when authenticating the user.");
            handleErrorRequest(respDTO, OAuth2ErrorCodes.SERVER_ERROR,
                    "Error occurred when authenticating the user.");
            return respDTO;
        }

        // if authentication failed, return the error back to the FE.
        if (!isAuthenticated) {
            log.info("User Authentication failed for user : " + authorizeDTO.getUsername());
            handleErrorRequest(respDTO, OAuth2ErrorCodes.ACCESS_DENIED,
                    "Authentication Failure, Invalid Credentials!");
            return respDTO;
        }

        // handle authorization
        OAuthAuthorizationCallback authzCallback = new OAuthAuthorizationCallback(authorizeDTO.getUsername(),
                authorizeDTO.getConsumerKey(),
                authorizeDTO.getScopes());
        try {
            OAuthAuthorizationHandler authzHandler = new OAuthAuthorizationHandler();
            authzHandler.handleAuthorization(authzCallback);
        } catch (IdentityOAuth2Exception e) {
            handleErrorRequest(respDTO, OAuth2ErrorCodes.SERVER_ERROR,
                    "Error occurred when authorizing the user.");
            return respDTO;
        }

        if(!authzCallback.isAuthorized() || authzCallback.isInvalidScope()){
            if(authzCallback.isInvalidScope()){
                if (log.isDebugEnabled()) {
                    log.debug("Invalid scope. :" +
                            " Username : " + authorizeDTO.getUsername() +
                            " Scope : " + OAuth2Util.buildScopeString(authorizeDTO.getScopes()));
                }
                handleErrorRequest(respDTO, OAuth2ErrorCodes.INVALID_SCOPE,
                        "Invalid Scope.");
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("User does not have right permissions to grant access to the resource :" +
                            " Username : " + authorizeDTO.getUsername() +
                            " Scope : " + OAuth2Util.buildScopeString(authorizeDTO.getScopes()));
                }
                handleErrorRequest(respDTO, OAuth2ErrorCodes.UNAUTHORIZED_CLIENT,
                        "Resource Owner does not have enough permissions to grant access.");
            }
            return respDTO;
        }


        OAuthIssuerImpl oauthIssuerImpl = new OAuthIssuerImpl(new MD5Generator());
        TokenMgtDAO tokenMgtDAO = new TokenMgtDAO();
        // Need to get the scope available in the authz callback. Scope might be different than the
        // scope that was present in the request.
        String scopeString = OAuth2Util.buildScopeString(authzCallback.getScope());

        try {
            if (ResponseType.CODE.toString().equals(authorizeDTO.getResponseType())) { // generate code

                String authorizationCode = oauthIssuerImpl.authorizationCode();

                tokenMgtDAO.storeAuthorizationCode(authorizationCode, authorizeDTO.getConsumerKey(),
                        scopeString, authorizeDTO.getUsername());

                if (log.isDebugEnabled()) {
                    log.debug("Issued Authorization Code to user : " +
                            authorizeDTO.getUsername() + ". Using the redirect url : " +
                            authorizeDTO.getCallbackUrl());
                }

                respDTO.setAuthorized(true);
                respDTO.setAuthorizationCode(authorizationCode);
                respDTO.setCallbackURI(authorizeDTO.getCallbackUrl());
                return respDTO;

            } else if (ResponseType.TOKEN.toString().equals(authorizeDTO.getResponseType())){   // generate token
                String accessToken = oauthIssuerImpl.accessToken();
                Timestamp timestamp = new Timestamp(new Date().getTime());
                // Default Validity Period
                long validityPeriod = 60 * 60;

                tokenMgtDAO.storeAccessToken(accessToken, null, authorizeDTO.getConsumerKey(),
                        authorizeDTO.getUsername(), timestamp, validityPeriod, scopeString,
                        OAuth2Constants.TokenStates.TOKEN_STATE_ACTIVE);

                if (log.isDebugEnabled()) {
                    log.debug("Issued AccessToken Code to user : " +
                            authorizeDTO.getUsername() + ". Using the redirect url : " +
                            authorizeDTO.getCallbackUrl());
                }

                respDTO.setAuthorized(true);
                respDTO.setAccessToken(accessToken);
                respDTO.setValidityPeriod(validityPeriod);
                respDTO.setCallbackURI(authorizeDTO.getCallbackUrl());
                return respDTO;

            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Unsupported Response Type." + authorizeDTO.getResponseType());
                }
                handleErrorRequest(respDTO, OAuth2ErrorCodes.UNSUPPORTED_RESP_TYPE,
                        "Unsupported Response Type.");
                return respDTO;
            }
        } catch (Exception e) {
            log.error("Error occurred when processing the authorization request. Returning an error back to client.", e);
            handleErrorRequest(respDTO, OAuth2ErrorCodes.SERVER_ERROR,
                    "Error occurred when processing the authorization request. Returning an error back to client.");
            return respDTO;
        }
    }

    /**
     * Check Whether the provided client_id and the callback URL are valid.
     *
     * @param clientId    client_id available in the request, Not null parameter.
     * @param callbackURI callback_uri available in the request, can be null.
     * @return <code>OAuth2ClientValidationResponseDTO</code> bean with validity information,
     *         callback, App Name, Error Code and Error Message when appropriate.
     */
    public OAuth2ClientValidationResponseDTO validateClientInfo(String clientId, String callbackURI) {
        OAuth2ClientValidationResponseDTO validationResponseDTO = new OAuth2ClientValidationResponseDTO();

        if (log.isDebugEnabled()) {
            log.debug("Validate Client information request for client_id : " + clientId +
                    " and callback_uri " + callbackURI);
        }

        try {
            OAuthAppDAO oAuthAppDAO = new OAuthAppDAO();
            OAuthAppDO appDO = oAuthAppDAO.getAppInformation(clientId);

            // There is no such Client ID being registered. So it is a request from an invalid client.
            if (appDO == null) {
                log.warn("No registered Client Id found against the given Client id : " + clientId);
                validationResponseDTO.setValidClient(false);
                validationResponseDTO.setErrorCode(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
                validationResponseDTO.setErrorMsg("Invalid Client Id.");
                return validationResponseDTO;
            }

            // Valid Client, No callback has provided. Use the callback provided during the registration.
            if (callbackURI == null) {
                validationResponseDTO.setValidClient(true);
                validationResponseDTO.setCallbackURL(appDO.getCallbackUrl());
                validationResponseDTO.setApplicationName(appDO.getApplicationName());
                return validationResponseDTO;
            }

            if (log.isDebugEnabled()) {
                log.debug("Registered App found for the given Client Id : " + clientId + " ,App Name : " +
                        appDO.getApplicationName() + ", Callback URL : " + appDO.getCallbackUrl());
            }

            // Valid Client with a callback url in the request. Check whether they are equal.
            if (appDO.getCallbackUrl().equals(callbackURI)) {
                validationResponseDTO.setValidClient(true);
                validationResponseDTO.setApplicationName(appDO.getApplicationName());
                validationResponseDTO.setCallbackURL(callbackURI);
                return validationResponseDTO;
            } else {    // Provided callback URL does not match the registered callback url.
                log.warn("Provided Callback URL does not match with the provided one.");
                validationResponseDTO.setValidClient(false);
                validationResponseDTO.setErrorCode(OAuth2ErrorCodes.INVALID_CALLBACK);
                validationResponseDTO.setErrorMsg("Registered callback does not match with the provided url.");
                return validationResponseDTO;
            }
        } catch (IdentityOAuthAdminException e) {
            log.error("Error when reading the Application Information.", e);
            validationResponseDTO.setValidClient(false);
            validationResponseDTO.setErrorCode(OAuth2ErrorCodes.SERVER_ERROR);
            validationResponseDTO.setErrorMsg("Error when processing the authorization request.");
            return validationResponseDTO;
        }
    }

    /**
     * Issue access token in exchange to an Authorization Grant.
     * @param tokenReqDTO <Code>OAuth2AccessTokenReqDTO</Code> representing the Access Token request
     * @return  <Code>OAuth2AccessTokenRespDTO</Code> representing the Access Token response
     */
    public OAuth2AccessTokenRespDTO issueAccessToken(OAuth2AccessTokenReqDTO tokenReqDTO) {

        if (log.isDebugEnabled()) {
            log.debug("Access Token Request Received with the Client Id : " + tokenReqDTO.getClientId() +
                    ", Grant Type : " + tokenReqDTO.getGrantType());
        }

        OAuth2AccessTokenRespDTO tokenRespDTO = new OAuth2AccessTokenRespDTO();
        // Authenticate the client.
        try {
            boolean authenticateClient = OAuth2Util.authenticateClient(
                    tokenReqDTO.getClientId(), tokenReqDTO.getClientSecret());
            if (!authenticateClient) {  // if client auth. fails
                tokenRespDTO.setError(true);
                tokenRespDTO.setErrorCode(OAuthError.TokenResponse.INVALID_CLIENT);
                tokenRespDTO.setErrorMsg("Client Authentication Failed. " +
                        "Provided client id or client secret is incorrect.");
                return tokenRespDTO;
            }
            // Issue the token
            AccessTokenIssuer tokenIssuer = new AccessTokenIssuer(getAuthzGrantHandler(tokenReqDTO));
            return tokenIssuer.issue(tokenReqDTO);

        } catch (Exception e) { // in case of an error, consider it as a system error
            log.error("Error when issuing the access token. ", e);
            tokenRespDTO.setError(true);
            tokenRespDTO.setErrorCode(OAuth2ErrorCodes.SERVER_ERROR);
            tokenRespDTO.setErrorMsg("Error when issuing the access token");
            return tokenRespDTO;
        }
    }

    private AuthorizationGrantHandler getAuthzGrantHandler(OAuth2AccessTokenReqDTO reqDTO){
        if(GrantType.AUTHORIZATION_CODE.toString().equals(reqDTO.getGrantType())){
            return new AuthorizationCodeValidator(reqDTO);
        } else if (GrantType.PASSWORD.toString().equals(reqDTO.getGrantType())){
            return new PasswordGrantHandler(reqDTO);
        }
        return null;
    }

    private void handleErrorRequest(OAuth2AuthorizeRespDTO respDTO, String errorCode,
                                    String errorMsg) {
        respDTO.setAuthorized(false);
        respDTO.setErrorCode(errorCode);
        respDTO.setErrorMsg(errorMsg);
    }

}
