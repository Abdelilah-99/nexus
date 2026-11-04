
package com.buy01.product_service.security;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import  jakarta.servlet.http.HttpServletRequest;
 
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver  {
   
   
   @Override
     public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().equals(AuthenticatedUser.class)
                && parameter.hasParameterAnnotation(CurrentUser.class);
    }

 

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer, NativeWebRequest nativeReq, WebDataBinderFactory binderFactory) throws Exception {
        
                HttpServletRequest servletReq = (HttpServletRequest) nativeReq.getNativeRequest();
                Object user = servletReq.getAttribute(UserContextFilter.USER_ATTRIBUTE);
        
            CurrentUser  annotation = parameter.getParameterAnnotation(CurrentUser.class);
            boolean requiired = annotation == null || annotation.requiired();
        if (user == null && requiired) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
            return  user;
    }

}