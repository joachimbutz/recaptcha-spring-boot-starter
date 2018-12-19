package com.github.mkopylec.recaptcha.webmvc.security;

import com.github.mkopylec.recaptcha.webmvc.RecaptchaProperties;
import com.github.mkopylec.recaptcha.webmvc.security.login.LoginFailuresClearingHandler;
import com.github.mkopylec.recaptcha.webmvc.security.login.LoginFailuresCountingHandler;
import com.github.mkopylec.recaptcha.webmvc.security.login.LoginFailuresManager;
import com.github.mkopylec.recaptcha.webmvc.validation.RecaptchaValidationException;
import com.github.mkopylec.recaptcha.webmvc.validation.RecaptchaValidator;
import com.github.mkopylec.recaptcha.webmvc.validation.ValidationResult;
import org.slf4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.github.mkopylec.recaptcha.webmvc.validation.ErrorCode.MISSING_USERNAME_REQUEST_PARAMETER;
import static com.github.mkopylec.recaptcha.webmvc.validation.ErrorCode.VALIDATION_HTTP_ERROR;
import static java.util.Collections.singletonList;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.Assert.notNull;

public class RecaptchaAuthenticationFilter extends UsernamePasswordAuthenticationFilter {

    private static final Logger log = getLogger(RecaptchaAuthenticationFilter.class);

    protected final RecaptchaValidator recaptchaValidator;
    protected final RecaptchaProperties recaptcha;
    protected final LoginFailuresManager failuresManager;
    protected final AuthenticationRequestParser requestParser;

    public RecaptchaAuthenticationFilter(
            RecaptchaValidator recaptchaValidator,
            RecaptchaProperties recaptcha,
            LoginFailuresManager failuresManager,
            AuthenticationRequestParser requestParser
    ) {
        this.recaptchaValidator = recaptchaValidator;
        this.recaptcha = recaptcha;
        this.failuresManager = failuresManager;
        this.requestParser = requestParser;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {
        if (getUsernameParameter() == null) {
            throw new RecaptchaAuthenticationException(singletonList(MISSING_USERNAME_REQUEST_PARAMETER));
        }
        request = new AuthenticationRequest(request, requestParser);
        if (failuresManager.isRecaptchaRequired(request)) {
            try {
                String recaptchaResponse = obtainRecaptchaResponse(request);
                ValidationResult result = recaptchaValidator.validate(recaptchaResponse, request.getRemoteAddr());
                if (result.isFailure()) {
                    throw new RecaptchaAuthenticationException(result.getErrorCodes());
                }
            } catch (RecaptchaValidationException ex) {
                boolean continueAuthentication = recaptcha.getSecurity().isContinueOnValidationHttpError();
                log.error("reCAPTCHA validation HTTP error. Continuing user authentication: " + continueAuthentication, ex);
                if (!continueAuthentication) {
                    throw new RecaptchaAuthenticationException(singletonList(VALIDATION_HTTP_ERROR));
                }
            }
        }
        return super.attemptAuthentication(request, response);
    }

    @Override
    public void setAuthenticationSuccessHandler(AuthenticationSuccessHandler successHandler) {
        if (!LoginFailuresClearingHandler.class.isAssignableFrom(successHandler.getClass())) {
            throw new IllegalArgumentException("Invalid login success handler. Handler must be an instance of " + LoginFailuresClearingHandler.class.getName() + " but is " + successHandler);
        }
        super.setAuthenticationSuccessHandler(successHandler);
    }

    @Override
    public void setAuthenticationFailureHandler(AuthenticationFailureHandler failureHandler) {
        if (!LoginFailuresCountingHandler.class.isAssignableFrom(failureHandler.getClass())) {
            throw new IllegalArgumentException("Invalid login failure handler. Handler must be an instance of " + LoginFailuresCountingHandler.class.getName() + " but is " + failureHandler);
        }
        super.setAuthenticationFailureHandler(failureHandler);
    }

    @Override
    public void setUsernameParameter(String usernameParameter) {
        super.setUsernameParameter(usernameParameter);
        failuresManager.setUsernameParameter(usernameParameter);
    }

    @Override
    public void afterPropertiesSet() {
        notNull(recaptchaValidator, "Missing recaptcha validator");
        notNull(recaptcha, "Missing recaptcha validation configuration properties");
        notNull(failuresManager, "Missing login failure manager");
    }

    @Override
    protected String obtainPassword(HttpServletRequest request) {
        return ((AuthenticationRequest) request).getParameters().getPassword();
    }

    @Override
    protected String obtainUsername(HttpServletRequest request) {
        return ((AuthenticationRequest) request).getParameters().getUsername();
    }

    protected String obtainRecaptchaResponse(HttpServletRequest request) {
        return ((AuthenticationRequest) request).getParameters().getRecaptchaResponse();
    }

//    protected String obtainRecaptchaResponse(HttpServletRequest request) {
//        return request.getParameter(recaptcha.getValidation().getResponseParameter());
//    }
}
