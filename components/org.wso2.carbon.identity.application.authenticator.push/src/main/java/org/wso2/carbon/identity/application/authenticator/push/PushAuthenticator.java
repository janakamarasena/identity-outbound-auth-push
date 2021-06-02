/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.wso2.carbon.identity.application.authenticator.push;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.wso2.carbon.identity.application.authentication.framework.AbstractApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.FederatedApplicationAuthenticator;
import org.wso2.carbon.identity.application.authentication.framework.context.AuthenticationContext;
import org.wso2.carbon.identity.application.authentication.framework.exception.AuthenticationFailedException;
import org.wso2.carbon.identity.application.authentication.framework.inbound.InboundConstants;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticatedUser;
import org.wso2.carbon.identity.application.authenticator.push.cache.AuthContextCache;
import org.wso2.carbon.identity.application.authenticator.push.cache.AuthContextCacheEntry;
import org.wso2.carbon.identity.application.authenticator.push.cache.AuthContextcacheKey;
import org.wso2.carbon.identity.application.authenticator.push.device.handler.DeviceHandler;
import org.wso2.carbon.identity.application.authenticator.push.device.handler.exception.PushDeviceHandlerClientException;
import org.wso2.carbon.identity.application.authenticator.push.device.handler.exception.PushDeviceHandlerServerException;
import org.wso2.carbon.identity.application.authenticator.push.device.handler.impl.DeviceHandlerImpl;
import org.wso2.carbon.identity.application.authenticator.push.device.handler.model.Device;
import org.wso2.carbon.identity.application.authenticator.push.dto.AuthDataDTO;
import org.wso2.carbon.identity.application.authenticator.push.dto.impl.AuthDataDTOImpl;
import org.wso2.carbon.identity.application.authenticator.push.exception.PushAuthenticatorException;
import org.wso2.carbon.identity.application.authenticator.push.notification.handler.RequestSender;
import org.wso2.carbon.identity.application.authenticator.push.notification.handler.impl.RequestSenderImpl;
import org.wso2.carbon.identity.application.authenticator.push.util.Config;
import org.wso2.carbon.identity.application.authenticator.push.validator.PushJWTValidator;
import org.wso2.carbon.identity.application.common.model.Property;
import org.wso2.carbon.user.api.UserStoreException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * This is the class that implements the push authenticator feature.
 */
public class PushAuthenticator extends AbstractApplicationAuthenticator
        implements FederatedApplicationAuthenticator {

    private static final long serialVersionUID = 8272421416671799253L;
    private static final Log log = LogFactory.getLog(PushAuthenticator.class);

    @Override
    public String getFriendlyName() {

        return PushAuthenticatorConstants.AUTHENTICATOR_FRIENDLY_NAME;
    }

    @Override
    public boolean canHandle(HttpServletRequest request) {

        return request.getParameter(PushAuthenticatorConstants.PROCEED_AUTH) != null;
    }

    @Override
    public String getContextIdentifier(javax.servlet.http.HttpServletRequest request) {

        return request.getParameter(InboundConstants.RequestProcessor.CONTEXT_KEY);
    }

    @Override
    public String getName() {

        return PushAuthenticatorConstants.AUTHENTICATOR_NAME;
    }

    @Override
    protected void initiateAuthenticationRequest(HttpServletRequest request, HttpServletResponse response,
                                                 AuthenticationContext context) throws AuthenticationFailedException {

        DeviceHandler deviceHandler = new DeviceHandlerImpl();
        AuthenticatedUser user = context.getSequenceConfig().getStepMap().
                get(context.getCurrentStep() - 1).getAuthenticatedUser();
        String sessionDataKey = request.getParameter(InboundConstants.RequestProcessor.CONTEXT_KEY);
        try {
            List<Device> deviceList;
            deviceList = deviceHandler.listDevices(user.getUserName(), user.getUserStoreDomain(),
                    user.getTenantDomain());
            request.getSession().setAttribute(PushAuthenticatorConstants.DEVICES_LIST, deviceList);
            JSONObject object;
            JSONArray array = new JSONArray();

            for (Device device : deviceList) {
                object = new JSONObject();
                object.put(PushAuthenticatorConstants.DEVICE_ID, device.getDeviceId());
                object.put(PushAuthenticatorConstants.DEVICE_NAME, device.getDeviceName());
                object.put(PushAuthenticatorConstants.DEVICE_MODEL, device.getDeviceModel());
                object.put(PushAuthenticatorConstants.LAST_TIME_USED, device.getLastUsedTime().toString());
                array.add(object);
            }

            AuthDataDTO authDataDTO = new AuthDataDTOImpl();
            context.setProperty(PushAuthenticatorConstants.CONTEXT_AUTH_DATA, authDataDTO);
            AuthContextCache.getInstance().addToCacheByRequestId(new AuthContextcacheKey(sessionDataKey),
                    new AuthContextCacheEntry(context));

            if (deviceList.size() == 1) {
                RequestSender requestSender = new RequestSenderImpl();
                requestSender.sendRequest(request, response, deviceList.get(0).getDeviceId(), sessionDataKey);
            } else {

                String string = JSONArray.toJSONString(array);
                Config config = new Config();
                String devicesPage;
                devicesPage = config.getDevicesPage(context)
                        + "?sessionDataKey=" + URLEncoder.encode(sessionDataKey, StandardCharsets.UTF_8.name())
                        + "&devices=" + URLEncoder.encode(string, StandardCharsets.UTF_8.name());
                response.sendRedirect(devicesPage);
            }

        } catch (PushDeviceHandlerServerException e) {
            throw new AuthenticationFailedException("Error occurred when trying to redirect to the registered devices"
                    + " page. Devices were not found for user: " + user.toFullQualifiedUsername() + ".", e);
        } catch (PushDeviceHandlerClientException e) {
            throw new AuthenticationFailedException("Error occurred when trying to redirect to registered devices page."
                    + " Authenticated user was not found.", e);
        } catch (SQLException e) {
            throw new AuthenticationFailedException("Error occurred when trying to get the device list for user: "
                    + user.toFullQualifiedUsername() + ".", e);
        } catch (UserStoreException e) {
            throw new AuthenticationFailedException("Error occurred when trying to get the authenticated user.", e);
        } catch (IOException e) {
            throw new AuthenticationFailedException("Error occurred when trying to redirect to the registered devices"
                    + " page for user: " + user.toFullQualifiedUsername() + ".", e);
        } catch (PushAuthenticatorException e) {
            throw new AuthenticationFailedException("Error occurred when trying to get user claims for user: "
                    + user.toFullQualifiedUsername() + ".", e);
        }

    }

    @Override
    protected void processAuthenticationResponse(HttpServletRequest httpServletRequest, HttpServletResponse
            httpServletResponse, AuthenticationContext authenticationContext) throws AuthenticationFailedException {

        AuthenticatedUser user = authenticationContext.getSequenceConfig().
                getStepMap().get(authenticationContext.getCurrentStep() - 1).getAuthenticatedUser();

        AuthContextcacheKey authContextCacheKey = new AuthContextcacheKey(httpServletRequest
                .getParameter(PushAuthenticatorConstants.SESSION_DATA_KEY));
        AuthenticationContext sessionContext = AuthContextCache
                .getInstance()
                .getValueFromCacheByRequestId(authContextCacheKey)
                .getAuthenticationContext();

        AuthDataDTO authDataDTO = (AuthDataDTO) sessionContext
                .getProperty(PushAuthenticatorConstants.CONTEXT_AUTH_DATA);

        String jwt = authDataDTO.getAuthToken();
        String serverChallenge = authDataDTO.getChallenge();

        PushJWTValidator validator = new PushJWTValidator();
        try {
            if (validateSignature(jwt, serverChallenge)) {
                String authStatus = validator.getAuthStatus(jwt);
                // TODO: Change Successful to Allowed
                if (authStatus.equals(PushAuthenticatorConstants.AUTH_REQUEST_STATUS_SUCCESS)) {
                    authenticationContext.setSubject(user);
                } else if (authStatus.equals(PushAuthenticatorConstants.AUTH_REQUEST_STATUS_DENIED)) {
                    String deniedPage = "/authenticationendpoint/retry.do"
                            + "?status=" + PushAuthenticatorConstants.AUTH_DENIED_PARAM
                            + "&statusMsg=" + PushAuthenticatorConstants.AUTH_DENIED_MESSAGE;
                    httpServletResponse.sendRedirect(deniedPage);
                } else {
                    String errorMessage = String.format("Authentication failed! Auth status for user" +
                            " '%s' is not available in JWT.", user);
                    throw new AuthenticationFailedException(errorMessage);
                }
            } else {
                authenticationContext.setProperty(PushAuthenticatorConstants.AUTHENTICATION_STATUS, true);
                String errorMessage = String
                        .format("Authentication failed! JWT signature is not valid for device: %s of user: %s.",
                                validator.getDeviceId(jwt), user);
                throw new AuthenticationFailedException(errorMessage);
            }

        } catch (IOException e) {
            String errorMessage = String
                    .format("Error occurred when redirecting to the request denied page for device: %s of user: %s.",
                            validator.getDeviceId(jwt), user);
            throw new AuthenticationFailedException(errorMessage, e);
        } catch (PushAuthenticatorException e) {
            String errorMessage = String
                    .format("Error occurred when trying to validate the JWT signature from device: %s of user: %s.",
                            validator.getDeviceId(jwt), user);
            throw new AuthenticationFailedException(errorMessage, e);
        }

        AuthContextCache.getInstance().clearCacheEntryByRequestId(new AuthContextcacheKey(
                validator.getSessionDataKey(jwt)));
    }

    @Override
    public List<Property> getConfigurationProperties() {

        List<Property> configProperties = new ArrayList<>();

        String firebaseServerKey = "Firebase Server Key";
        Property serverKeyProperty = new Property();
        serverKeyProperty.setName(PushAuthenticatorConstants.SERVER_KEY);
        serverKeyProperty.setDisplayName(firebaseServerKey);
        serverKeyProperty.setDescription("Enter the firebase server key ");
        serverKeyProperty.setDisplayOrder(0);
        serverKeyProperty.setRequired(true);
        serverKeyProperty.setConfidential(true);
        configProperties.add(serverKeyProperty);

        String fcmUrl = "Firebase url";
        Property fcmUrlProperty = new Property();
        fcmUrlProperty.setName(PushAuthenticatorConstants.FCM_URL);
        fcmUrlProperty.setDisplayName(fcmUrl);
        fcmUrlProperty.setDescription("Enter the url of firebase endpoint ");
        fcmUrlProperty.setDisplayOrder(1);
        fcmUrlProperty.setConfidential(false);
        configProperties.add(fcmUrlProperty);
        return configProperties;
    }

    /**
     * Validate the signature using the JWT received from mobile app
     *
     * @param jwt       JWT generated from mobile app
     * @param challenge Challenge stored in cache to correlate with JWT
     * @return Boolean for validity of the signature
     * @throws PushAuthenticatorException Exception in push authenticator
     */
    private boolean validateSignature(String jwt, String challenge)
            throws PushAuthenticatorException {

        boolean isValid;
        DeviceHandler handler = new DeviceHandlerImpl();

        PushJWTValidator validator = new PushJWTValidator();
        String deviceId = validator.getDeviceId(jwt);
        String publicKeyStr;
        try {
            publicKeyStr = handler.getPublicKey(deviceId);
        } catch (SQLException e) {
            String errorMessage = String
                    .format("Error occurred when trying to get public key for device: %s from the database.", deviceId);
            throw new PushAuthenticatorException(errorMessage, e);
        } catch (IOException e) {
            throw new PushAuthenticatorException("Error occurred when trying to get public key for device: "
                    + deviceId + ".", e);
        }

        try {
            isValid = validator.validate(jwt, publicKeyStr, challenge);
        } catch (Exception e) {
            throw new PushAuthenticatorException("Error occurred when validating the signature."
                    + " Failed to parse string to JWT.", e);
        }
        return isValid;
    }

}