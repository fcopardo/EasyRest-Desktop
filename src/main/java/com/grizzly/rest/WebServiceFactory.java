/*
 * Copyright (c) 2014. Francisco Pardo Baeza
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.grizzly.rest;

import com.grizzly.rest.Model.RestResults;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.grizzly.rest.Model.sendRestData;
import org.springframework.http.HttpHeaders;
import rx.Subscriber;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.io.File;
import java.util.*;

/**
 * Created on 24/03/14.
 * Creates instances of parametrized AdvancedRestCall
 */
public class WebServiceFactory implements CacheProvider{

    private HttpHeaders requestHeaders = new HttpHeaders();
    private HttpHeaders responseHeaders = new HttpHeaders();
    private long globalCacheTime = 899999;
    private int timeOutValue = 60000;
    private static HashMap<String, String> cachedRequests = new HashMap<>();
    private String baseUrl = "";
    private MappingJackson2HttpMessageConverter jacksonConverter;

    private Map<String, List<Subscriber<RestResults>>> subscribers;

    public HttpHeaders getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(HttpHeaders requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    /*public HttpHeaders getResponseHeaders() {
        return responseHeaders;
    }*/

    public void resetHeaders() {
        requestHeaders = new HttpHeaders();
        responseHeaders = new HttpHeaders();
    }

    /*public void setResponseHeaders(HttpHeaders responseHeaders) {
        this.responseHeaders = responseHeaders;
    }*/

    public void setGlobalCacheTime(long time){
        globalCacheTime = time;
    }

    public void setTimeOutValue(int miliseconds){
        if(miliseconds>=0){
            timeOutValue = miliseconds;
        }
        else{
            throw new IllegalArgumentException("The timeout must be greater than zero");
        }
    }

    public void setBaseUrl(String BaseUrl){
        baseUrl = BaseUrl;
    }

    public WebServiceFactory() {
    }


    public <T extends sendRestData, X> EasyRestCall<T, X, Void> getRestCallInstance(Class<T> entityClass, Class<X> responseClass) {

        return this.getRestCallInstance(entityClass, responseClass, Void.class, false);
    }

    public <T extends sendRestData, X> EasyRestCall<T, X, Void> getRestCallInstance(Class<T> entityClass, Class<X> responseClass, boolean isTest) {

        return this.getRestCallInstance(entityClass, responseClass, Void.class, isTest);
    }

    public <T extends sendRestData, X, M> EasyRestCall<T, X, M> getRestCallInstance(Class<T> entityClass, Class<X> responseClass, Class<M> errorBodyClass) {

        return this.getRestCallInstance(entityClass, responseClass, errorBodyClass, false);
    }

    public <T extends sendRestData, X, M> EasyRestCall<T, X, M> getRestCallInstance(Class<T> entityClass, Class<X> responseClass, Class<M> errorBodyClass, boolean isTest) {

        EasyRestCall<T, X, M> myRestCall = null;

        if(isTest) {
            myRestCall = new EasyRestCall<>(entityClass, responseClass, errorBodyClass, 1);
        }
        else {
            myRestCall = new EasyRestCall<>(entityClass, responseClass, errorBodyClass);
        }

        myRestCall.setCacheProvider(this);
        myRestCall.setCacheTime(globalCacheTime);

        if(requestHeaders!= null && !requestHeaders.isEmpty()){
           myRestCall.setRequestHeaders(requestHeaders);
        }
        if(!baseUrl.isEmpty() && baseUrl.trim().equalsIgnoreCase("") && baseUrl != null){
            myRestCall.setUrl(baseUrl);
        }
        if(jacksonConverter!=null){
            //myRestCall.setJacksonMapper(jacksonConverter);
        }
        myRestCall.setTimeOut(timeOutValue);


        return myRestCall;
    }

    public <T, X> GenericRestCall<T, X, Void> getGenericRestCallInstance(Class<T> entityClass, Class<X> responseClass, boolean isTest) {

        return this.getGenericRestCallInstance(entityClass, responseClass, Void.class, isTest);
    }

    public <T, X> GenericRestCall<T, X, Void> getGenericRestCallInstance(Class<T> entityClass, Class<X> responseClass) {

        return this.getGenericRestCallInstance(entityClass, responseClass, Void.class, false);
    }

    public <T, X, M> GenericRestCall<T, X, M> getGenericRestCallInstance(Class<T> entityClass, Class<X> responseClass, Class<M> errorBodyClass) {

        return this.getGenericRestCallInstance(entityClass, responseClass, errorBodyClass, false);
    }

    public <T, X, M> GenericRestCall<T, X, M> getGenericRestCallInstance(Class<T> entityClass, Class<X> responseClass, Class<M> errorBodyClass, boolean isTest) {

        GenericRestCall<T, X, M> myRestCall = null;

        if(isTest) {
            myRestCall = new GenericRestCall<>(entityClass, responseClass, errorBodyClass, 1);
        }
        else {
            myRestCall = new GenericRestCall<>(entityClass, responseClass, errorBodyClass);
        }

        try{
            myRestCall.setCacheProvider(this);
            myRestCall.setCacheTime(globalCacheTime);
        }
        catch(NullPointerException e){
            e.printStackTrace();
        }
        if(requestHeaders!= null && !requestHeaders.isEmpty()){
            myRestCall.setRequestHeaders(requestHeaders);
        }
        if(!baseUrl.isEmpty() && baseUrl.trim().equalsIgnoreCase("") && baseUrl != null){
            myRestCall.setUrl(baseUrl);
        }
        if(jacksonConverter!=null){
            myRestCall.setJacksonMapper(jacksonConverter);
        }
        myRestCall.setTimeOut(timeOutValue);

        return myRestCall;
    }

    @Override
    public <T, X, M> boolean setCache(GenericRestCall<T, X, M> myRestCall, Class<X> responseClass, Class<T> entityClass, Class<M> errorBodyClass){

        boolean bol = false;

        if(!myRestCall.getUrl().trim().equalsIgnoreCase("") && myRestCall != null)
        {

            if(!responseClass.getCanonicalName().equalsIgnoreCase(Void.class.getCanonicalName()) ){


                /*if(cachedRequests.containsKey(myRestCall.getUrl())){
                    myRestCall.setCachedFileName(cachedRequests.get(myRestCall.getUrl()));
                    return true;
                }
                else{
                    cachedRequests.put(myRestCall.getUrl(), myRestCall.getCachedFileName());
                }*/
            }
            return false;
        }

        return bol;
    }

    /**
     * Factory getter method. Returns either the current instance of the MappingJackson2HttpConverter,
     * or a initialized one wih FAIL_ON_UNKNOW_PROPERTIES and FAIL_ON_INVALID_SUBTYPE set to false.
     * @return the jacksonConverter to be used by all the rest calls created by this factory.
     */
    public MappingJackson2HttpMessageConverter getJacksonConverter() {
        if(jacksonConverter == null){
            MappingJackson2HttpMessageConverter jacksonConverter = new MappingJackson2HttpMessageConverter();
            jacksonConverter.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            jacksonConverter.getObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        }
        return jacksonConverter;
    }

    /**
     * Allows setting a custom MappingJackson2HttpConverter
     * @param jacksonConverter the converter to be set.
     */
    public void setJacksonConverter(MappingJackson2HttpMessageConverter jacksonConverter) {
        this.jacksonConverter = jacksonConverter;
    }
}
