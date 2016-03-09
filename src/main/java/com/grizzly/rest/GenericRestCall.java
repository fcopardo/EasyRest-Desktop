/*
 * Copyright (c) 2014. Francisco Pardo Baeza.
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


import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grizzly.rest.Definitions.DefinitionsHttpMethods;
import com.grizzly.rest.Model.*;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.*;
import rx.Subscriber;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Rest class based on the Spring RestTemplate. Allows to send T objects, and retrieves a X result. All classes
 * should be annotated to allow the proper serialization and deserialization. Headers, URL an method must be provided using the setters.
 *
 * @author Fco Pardo
 */
public class GenericRestCall<T, X, M> implements Runnable {

    /**
     * Constants for method calls
     */
    public static final String CONTENT_TYPE = "content_type";
    public static final String JSON = "application/json";
    public static final String XML = "application/xml";

    /*private static final int ERROR = 1;
    private static final int SERVER_ERROR = 2;
    private static final int CLIENT_ERROR = 3;*/

    /**
     * Class members
     * T: the Entity representing the data to be sent.
     * X: The Json.Entity to be returned.
     */
    private Class<T> entityClass;
    private Class<X> jsonResponseEntityClass;
    private Class<M> errorResponseEntityClass;
    private T entity;
    private X jsonResponseEntity;
    private String singleArgument;
    private String url = "";
    private RestTemplate restTemplate = new RestTemplate();
    private HttpMethod methodToCall;
    private HttpHeaders requestHeaders = new HttpHeaders();
    private HttpHeaders responseHeaders;
    private HttpStatus responseStatus = HttpStatus.I_AM_A_TEAPOT;
    private boolean result = false;
    private HttpMethod fixedMethod;
    private Map<DeserializationFeature, Boolean> deserializationFeatureMap;
    //private boolean noReturn = false;

    private afterTaskCompletion<X> taskCompletion;
    private afterTaskFailure<M> taskFailure;
    private afterServerTaskFailure<M> serverTaskFailure;
    private afterClientTaskFailure<M> clientTaskFailure;
    private com.grizzly.rest.Model.commonTasks commonTasks;

    //private int //errorType = 0;

    private String waitingMessage;
    private Exception failure;
    private HttpServerErrorException serverFailure;
    private HttpClientErrorException clientFailure;
    private ResourceAccessException connectionException;
    private boolean bodyless = false;
    private String cachedFileName = "";
    private boolean enableCache = true;
    private CacheProvider cacheProvider = null;
    private long cacheTime = 899999;
    private boolean reprocessWhenRefreshing = false;
    private boolean automaticCacheRefresh = false;
    private String errorResponse = "";

    private String basePath = System.getProperty("user.dir");

    private List<Subscriber<RestResults<X>>> mySubscribers;
    private MappingJackson2HttpMessageConverter jacksonConverter;

    /**
     * Base constructor.
     *
     * @param EntityClass
     * @param JsonResponseEntityClass
     */
    public GenericRestCall(Class<T> EntityClass, Class<X> JsonResponseEntityClass, Class<M> ErrorResponseEntityClass) {
        this.entityClass = EntityClass;
        this.jsonResponseEntityClass = JsonResponseEntityClass;
        this.errorResponseEntityClass = ErrorResponseEntityClass;
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        ((HttpComponentsClientHttpRequestFactory)restTemplate.getRequestFactory()).setConnectTimeout(60000);
        ((HttpComponentsClientHttpRequestFactory)restTemplate.getRequestFactory()).setReadTimeout(60000);
    }

    /**
     * Base constructor for testing.
     *
     * @param EntityClass
     * @param JsonResponseEntityClass
     */
    public GenericRestCall(Class<T> EntityClass, Class<X> JsonResponseEntityClass, Class<M> ErrorResponseEntityClass, int test) {
        this.entityClass = EntityClass;
        this.jsonResponseEntityClass = JsonResponseEntityClass;
        this.errorResponseEntityClass = ErrorResponseEntityClass;
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
    }

    /**
     * Base constructor for petitions with empty return
     *
     * @param EntityClass
     */
    public GenericRestCall(Class<T> EntityClass, HttpMethod Method) {
        this.entityClass = EntityClass;
        requestHeaders.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        if(DefinitionsHttpMethods.isHttpMethod(Method)) {
            fixedMethod = methodToCall = Method;
        }
    }

    /**
     * Setter for the request's timeout
     */
    public GenericRestCall<T, X, M> setTimeOut(int miliseconds){
        try{
            ((HttpComponentsClientHttpRequestFactory)restTemplate.getRequestFactory()).setConnectTimeout(miliseconds);
            ((HttpComponentsClientHttpRequestFactory)restTemplate.getRequestFactory()).setReadTimeout(miliseconds);
        }
        catch(ClassCastException e){
            System.out.println("The factory used wasn't HtppComponents");
        }
        return this;
    }

    /**
     * Returns the argument entity.
     * @return a subclass of BaseModel
     */
    public T getEntity() {
        return entity;
    }

    /**
     * Sets the argument entity.
     * @param entity a class implementing sendRestData, to be sent in the request.
     */
    public GenericRestCall<T, X, M> setEntity(T entity) {
        this.entity = entity;
        return this;
    }

    /**
     * Returns the response of the rest call, in X form.
     * @return an X instance of the rest call response.
     */
    public X getJsonResponseEntity() {
        return jsonResponseEntity;
    }

    /**
     * Returns the response of the rest call, in F form. F is defined in the method call.
     * @param objectClass The class to be returned.
     * @param <F> Any class. It should match the expected return type.
     * @return The response in F class.
     */
    public <F> F getJsonResponseEntity(Class<F> objectClass) {
        return (F)jsonResponseEntity;
    }

    /**
     * Sets the response entity.
     * @param jsonResponseEntity an instance of X.
     */
    public void setJsonResponseEntity(X jsonResponseEntity) {
        this.jsonResponseEntity = jsonResponseEntity;
    }

    public String getErrorResponse(){
        return errorResponse;
    }

    /**
     * Returns the Http headers to be used.
     * @return an instance of HttpHeaders.
     */
    public HttpHeaders getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * Sets the Http headers to be used.
     * @param requestHeaders an instance of HttpHeaders.
     */
    public GenericRestCall<T, X, M> setRequestHeaders(HttpHeaders requestHeaders) {
        this.requestHeaders = requestHeaders;
        return this;
    }

    /**
     * Returns the headers from the response.
     * @return an HttpHeaders instance from the request response.
     */
    public HttpHeaders getResponseHeaders() {
        if(responseHeaders==null) {
            responseHeaders = new HttpHeaders();
        }
        return responseHeaders;
    }

    private void setResponseHeaders(HttpHeaders responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    /**
     * Returns the REST method to be called when the service call is executed.
     * @return a String with the method.
     */
    public HttpMethod getMethodToCall() {
        return methodToCall;
    }

    public Map<DeserializationFeature, Boolean> getdeserializationFeatureMap() {
        if(deserializationFeatureMap == null) deserializationFeatureMap = new HashMap<>();
        return deserializationFeatureMap;
    }

    public void setdeserializationFeatureMap(Map<DeserializationFeature, Boolean> deserializationFeatureMap) {
        this.deserializationFeatureMap = deserializationFeatureMap;
    }

    public void addDeserializationFeature(DeserializationFeature deserializationFeature, boolean activated) {
        getdeserializationFeatureMap();
        deserializationFeatureMap.put(deserializationFeature, activated);
    }

    /*public boolean isResult() {
        return result;
    }

    public String getSingleArgument() {
        return singleArgument;
    }

    public void setSingleArgument(String singleArgument) {
        this.singleArgument = singleArgument;
    }*/

    public HttpStatus getResponseStatus() {
        if(responseStatus==null) responseStatus = HttpStatus.I_AM_A_TEAPOT;
        return responseStatus;
    }

    /**
     * Allows to set the Http method to call. If a invalid or unsupported method is passed, it will be ignored.
     *
     * @param MethodToCall a valid http method.
     */
    public GenericRestCall<T, X, M> setMethodToCall(HttpMethod MethodToCall) {

        if(fixedMethod == null){

            if(DefinitionsHttpMethods.isHttpMethod(MethodToCall)) {
                this.methodToCall = MethodToCall;
            }
        }
        else {
            methodToCall = fixedMethod;
        }
        return this;
    }


    public GenericRestCall<T, X, M> addUrlParams(Map<String, Object> urlParameters) {

        if(urlParameters != null && !urlParameters.isEmpty()){
            String customUrl = getUrl();
            StringBuilder builder = new StringBuilder();

            String separator = "?";

            builder.append(customUrl);
            for(String key: urlParameters.keySet()){
                Object value = urlParameters.get(key);

                if(value.toString().contains(" ")){
                    value = value.toString().replace(" ", "+");
                }

                builder.append(separator);
                builder.append(key);
                builder.append("=");
                builder.append(value);

                separator = "&";
            }
            setUrl(builder.toString());
        }
        return this;
    }

    /**
     * Sets the URL to be used.
     * @param Url the URL of the rest call.
     */
    public GenericRestCall<T, X, M> setUrl(String Url) {
        url = Url;
        return this;
    }

    public String getUrl(){
        return getURI().toString();
    }

    private URI getURI() {
        URI uri = null;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return uri;
    }

    /**
     * Interface. Allows to attach a body of code to be executed after a successful rest call.
     * @param task a class implementing the afterTaskCompletion interface.
     */
    public GenericRestCall<T, X, M> setTaskCompletion(afterTaskCompletion task){
        this.taskCompletion = task;
        return this;
    }

    /**
     * Interface. Allows to attach a body of code to be executed after a failed rest call.
     * @param taskFailure a class implementing the afterTaskFailure interface.
     */
    public GenericRestCall<T, X, M> setTaskFailure(afterTaskFailure taskFailure) {

        this.taskFailure = taskFailure;
        return this;
    }

    /**
     * Interface to be executed when a server error occurs.
     * @param serverTaskFailure an instance of the afterServerTaskFailure interface
     */
    public GenericRestCall<T, X, M> setServerTaskFailure(afterServerTaskFailure<M> serverTaskFailure) {
        this.serverTaskFailure = serverTaskFailure;
        return this;
    }

    /**
     * Interface to be executed when a client error arises.
     * @param clientTaskFailure an instance of the afterClientTaskFailure interface
     */
    public GenericRestCall<T, X, M> setClientTaskFailure(afterClientTaskFailure<M> clientTaskFailure) {
        this.clientTaskFailure = clientTaskFailure;
        return this;
    }

    /**
     * Interface to be executed after all processes are finalized, no matter the result.
     * @param commonTasks an instance of the commonTasks interface.
     */
    public GenericRestCall<T, X, M> setCommonTasks(com.grizzly.rest.Model.commonTasks commonTasks) {
        this.commonTasks = commonTasks;
        return this;
    }

    public GenericRestCall<T, X, M> addHeader(String header, String value){
        this.getRequestHeaders().add(header, value);
        return this;
    }

    public String getWaitingMessage() {
        return waitingMessage;
    }

    public void setWaitingMessage(String waitingMessage) {
        this.waitingMessage = waitingMessage;
    }

    /**
     * Allows to set the "bodyless" argument. If true, a post request can be sent without a body.
     * @param bol the value to set.
     */
    public void setBodyless(boolean bol){
        bodyless = bol;
    }

    /**
     * Returns the "bodyless" state.
     * @return
     */
    public boolean isBodyless(){
        return bodyless;
    }

    /**
     * Process the response of the rest call and assigns the body to the responseEntity.
     * @param response a valid response.
     * @return true or false.
     */
    private boolean processResponseWithData(ResponseEntity<X> response){
        responseStatus = response.getStatusCode();
        this.setResponseHeaders(response.getHeaders());
        if(!response.getBody().equals(null)) {
            jsonResponseEntity = response.getBody();

            if(enableCache){
                createSolidCache();
            }
        }
        return true;
    }

    /**
     * Process the response of the rest call.
     * @param response a valid response.
     * @return true or false.
     */
    private boolean processResponseWithouthData(ResponseEntity<X> response){
        responseStatus = response.getStatusCode();
        this.setResponseHeaders(response.getHeaders());
        return true;
    }

    void setCachedFileName(String s){

        if(s.contains(basePath + File.separator + "EasyRest" + File.separator
                + jsonResponseEntityClass.getSimpleName())){
            cachedFileName = s;
        }
        else{
            cachedFileName = basePath + File.separator + "EasyRest" + File.separator
                    + jsonResponseEntityClass.getSimpleName()+s;
        }

    }

    String getCachedFileName(){

        if(cachedFileName.isEmpty() || cachedFileName.equalsIgnoreCase("")){

            String queryKey = "";
            try {
                queryKey = EasyRest.getHashOne(getURI().getAuthority() + getURI().getPath().replace("/", "_") + getURI().getQuery());
            } catch (NoSuchAlgorithmException e) {
                queryKey = getURI().getAuthority()+getURI().getPath().replace("/", "_")+getURI().getQuery();
                e.printStackTrace();
            }

            String fileName = basePath + File.separator + "EasyRest" + File.separator
                    + jsonResponseEntityClass.getSimpleName()
                    +queryKey;

            return fileName;
        }
        System.out.println("CACHE: " + cachedFileName);
        return cachedFileName;
    }

    private void createSolidCache(){

        EasyRest.cacheRequest(getCachedFileName(), jsonResponseEntity);
        new Thread(new Runnable() {
            @Override
            public void run() {
                ObjectMapper mapper = new ObjectMapper();

                try {
                    File dir = new File(basePath + File.separator + "EasyRest");
                    dir.mkdir();
                    File f = new File(getCachedFileName());
                    mapper.writeValue(f, jsonResponseEntity);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    private boolean getFromSolidCache()
    {
        if(EasyRest.getCachedRequest(getCachedFileName())!=null){
            jsonResponseEntity = (X) EasyRest.getCachedRequest(getCachedFileName());
            return true;
        }

        ObjectMapper mapper = new ObjectMapper();

        if(cacheProvider!=null){
            cacheProvider.setCache(this, jsonResponseEntityClass, entityClass, errorResponseEntityClass);
        }

        try {
            File dir = new File(basePath + File.separator + "EasyRest");
            dir.mkdir();
            File f = new File(getCachedFileName());
            if(f.exists()){
                jsonResponseEntity = mapper.readValue(f, jsonResponseEntityClass);
                this.responseStatus = HttpStatus.OK;
                EasyRest.cacheRequest(getCachedFileName(), jsonResponseEntity);
                return true;
            }
            System.out.println("EasyRest - Cache Failure - FileName: " + getCachedFileName());
        } catch (JsonGenerationException e) {

            this.failure = e;
            e.printStackTrace();

        } catch (JsonMappingException e) {

            this.failure = e;
            e.printStackTrace();

        } catch (IOException e) {

            this.failure = e;
            e.printStackTrace();

        }
        catch(NullPointerException e){
            this.failure = e;
            e.printStackTrace();
        }
        return false;

    }

    public GenericRestCall<T, X, M> isCacheEnabled(boolean bol){
        enableCache = bol;
        return this;
    }

    void setCacheProvider(CacheProvider provider){
        cacheProvider = provider;
    }
    
    public GenericRestCall<T, X, M> setCacheTime(Long time){
        cacheTime = time;
        return this;
    }

    public void setReprocessWhenRefreshing(boolean reprocessWhenRefreshing) {
        this.reprocessWhenRefreshing = reprocessWhenRefreshing;
    }

    public void setAutomaticCacheRefresh(boolean automaticCacheRefresh) {
        this.automaticCacheRefresh = automaticCacheRefresh;
    }

    public MappingJackson2HttpMessageConverter getJacksonMapper() {

        if (jacksonConverter == null) {
            jacksonConverter = new MappingJackson2HttpMessageConverter();
            jacksonConverter.getObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            jacksonConverter.getObjectMapper().configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
        }

        if (!getdeserializationFeatureMap().isEmpty()) {
            for (DeserializationFeature feature : getdeserializationFeatureMap().keySet()) {
                jacksonConverter.getObjectMapper().configure(feature, getdeserializationFeatureMap().get(feature));
            }
        }
        return jacksonConverter;
    }

    public GenericRestCall<T, X, M> setJacksonMapper(MappingJackson2HttpMessageConverter customConverter) {
        jacksonConverter = customConverter;
        return this;
    }

    public GenericRestCall<T, X, M> addSuccessSubscriber(Subscriber<RestResults<X>> subscriber){
        if(mySubscribers==null) mySubscribers = new ArrayList<>();
        mySubscribers.add(subscriber);
        return this;
    }

    public GenericRestCall<T, X, M> deleteSuccessSubscriber(Subscriber<RestResults<X>> subscriber){
        if(mySubscribers==null) mySubscribers = new ArrayList<>();
        mySubscribers.remove(subscriber);
        return this;
    }

    /**
     * Post call. Sends T in J form to retrieve a X result.
     */
    private void doPost() {

        try {

            HttpEntity<?> requestEntity;
            if(!bodyless){
                requestEntity = new HttpEntity<Object>(entity, requestHeaders);
            }
            else{
                requestEntity = new HttpEntity<Object>(requestHeaders);
            }

            List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
            messageConverters.add(getJacksonMapper());
            restTemplate.setMessageConverters(messageConverters);

            try {

                if(jsonResponseEntityClass.getCanonicalName().equalsIgnoreCase(Void.class.getCanonicalName())){
                    ResponseEntity response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Void.class);
                    result = this.processResponseWithouthData(response);
                }
                else{

                    ResponseEntity<X> response = null;

                    File f = new File(getCachedFileName());
                    if(f.exists() && ((enableCache && Calendar.getInstance(Locale.getDefault()).getTimeInMillis()-f.lastModified()<=cacheTime))) {
                        getFromSolidCache();
                        if(this.automaticCacheRefresh)this.createDelayedCall(reprocessWhenRefreshing);
                        result = true;
                    }
                    else{
                        response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, jsonResponseEntityClass);
                        result = this.processResponseWithData(response);
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException | HttpServerErrorException e) {
                handleException(e);
            }
            catch(org.springframework.web.client.ResourceAccessException e){
                failure = e;
                connectionException = e;
                //errorType = ERROR;
                System.out.println("The error was caused by the body "+entityClass.getCanonicalName());
                System.out.println(" and the response " + jsonResponseEntityClass.getCanonicalName());
                System.out.println(" in the url " + url);
                e.printStackTrace();
            }
            catch(org.springframework.http.converter.HttpMessageNotReadableException e){
                System.out.println("Conversion error the error found "+e.getMessage());
                System.out.println(" the expected response type was " + jsonResponseEntityClass.getCanonicalName());
                System.out.println(" in the url " + url);
                e.printStackTrace();
            }
        } catch (Exception e) {
            handleException(e);
        }

    }

    /**
     * Get call. It doesn't send anything, but retrieves X.
     */
    private void doGet() {

        try {

            List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
            messageConverters.add(new MappingJackson2HttpMessageConverter());
            restTemplate.setMessageConverters(messageConverters);

            try {
                HttpEntity<?> requestEntity = new HttpEntity<Object>(requestHeaders);

                if (jsonResponseEntityClass.getCanonicalName().equalsIgnoreCase(Void.class.getCanonicalName())) {
                    ResponseEntity response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, Void.class);
                    result = this.processResponseWithouthData(response);
                } else {
                    ResponseEntity<X> response = null;

                    File f = new File(getCachedFileName());

                    if(f.exists() && ((enableCache && Calendar.getInstance(Locale.getDefault()).getTimeInMillis()-f.lastModified()<=cacheTime))) {
                        getFromSolidCache();
                        if(this.automaticCacheRefresh)this.createDelayedCall(reprocessWhenRefreshing);
                        result = true;
                        if(EasyRest.isDebugMode())System.out.println("EasyRest - We got from cache!");
                    } else {
                        response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, jsonResponseEntityClass);
                        result = this.processResponseWithData(response);
                        if(EasyRest.isDebugMode())System.out.println("EasyRest - We got from service, cache failed!");
                    }
                }

            } catch (org.springframework.web.client.HttpClientErrorException | HttpServerErrorException e ) {
                handleException(e);

            }
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Delete call. Sends T to retrieve a C result.
     */
    private void doDelete() {

        try {

            HttpEntity<?> requestEntity = new HttpEntity<Object>(entity, requestHeaders);
            List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
            messageConverters.add(new MappingJackson2HttpMessageConverter());
            restTemplate.setMessageConverters(messageConverters);

            try {

                if(jsonResponseEntityClass.getCanonicalName().equalsIgnoreCase(Void.class.getCanonicalName())){
                    ResponseEntity response = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, Void.class);
                    result = this.processResponseWithouthData(response);
                }
                else{
                    ResponseEntity<X> response = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, jsonResponseEntityClass);
                    result = this.processResponseWithData(response);
                }
            } catch (org.springframework.web.client.HttpClientErrorException | HttpServerErrorException e) {
                handleException(e);
            }
        } catch (Exception e) {
            handleException(e);
        }

    }

    /**
     * Delete call. Sends a String and retrieves another String.
     *
     * @param singleArgument
     */
    private void doDelete(String singleArgument) {

        try {

            HttpEntity<String> requestEntity = new HttpEntity<String>(singleArgument, requestHeaders);
            List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
            /*
             * commented: testing GSON instead of Jackson as a message converter
             * */
            messageConverters.add(new MappingJackson2HttpMessageConverter());
            //messageConverters.create(new GsonHttpMessageConverter());
            restTemplate.setMessageConverters(messageConverters);

            try {

                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE, requestEntity, String.class);
                HttpStatus status = response.getStatusCode();
                if (status == HttpStatus.OK || status == HttpStatus.ACCEPTED || status == HttpStatus.CREATED) {
                    this.result = true;
                } else {
                    this.result = false;
                }
            } catch (org.springframework.web.client.HttpClientErrorException | HttpServerErrorException e) {
                handleException(e);
            }
        } catch (Exception e) {
            handleException(e);
        }

    }

    /**
     * Put call. Sends T in J form to retrieve a X result.
     */
    private void doPut() {

        try {

            HttpEntity<?> requestEntity;
            if(!bodyless){
                requestEntity = new HttpEntity<Object>(entity, requestHeaders);
            }
            else{
                requestEntity = new HttpEntity<Object>(requestHeaders);
            }

            List<HttpMessageConverter<?>> messageConverters = new ArrayList<HttpMessageConverter<?>>();
            messageConverters.add(new MappingJackson2HttpMessageConverter());
            restTemplate.setMessageConverters(messageConverters);

            try {
                if(jsonResponseEntityClass.getCanonicalName().equalsIgnoreCase(Void.class.getCanonicalName())){
                    ResponseEntity response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, Void.class);
                    result = this.processResponseWithouthData(response);
                }
                else{

                    ResponseEntity<X> response = null;

                    File f = new File(getCachedFileName());
                    if(f.exists() && ((enableCache && Calendar.getInstance(Locale.getDefault()).getTimeInMillis()-f.lastModified()<=cacheTime))) {
                        getFromSolidCache();
                        if(this.automaticCacheRefresh)this.createDelayedCall(reprocessWhenRefreshing);
                        result = true;
                    }
                    else{
                        response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, jsonResponseEntityClass);
                        result = this.processResponseWithData(response);
                    }
                }
            } catch (org.springframework.web.client.HttpClientErrorException | HttpServerErrorException e) {
                handleException(e);
            }
        } catch (Exception e) {
            handleException(e);
        }

    }

    /**
     * Put here any calls to tasks to do after the execution of the thread.
     * It allows to retrieve the boolean result of the rest call in an asynchronous way.
     *
     * @param result the result of the doInBackground operation.
     */
    protected void onPostExecute(Boolean result) {

        this.result = result.booleanValue();

        if(enableCache){
            getFromSolidCache();
        }

        if(responseStatus.value()>399) result = false;

        if(result){
            if(EasyRest.isDebugMode()){
                if(getResponseHeaders()!=null){
                    System.out.println("EasyRest - Response Headers");
                    for(String s: getResponseHeaders().keySet()){
                        System.out.println("EasyRest - "+ s + ":" + getResponseHeaders().get(s));
                    }
                }
            }
            if(taskCompletion != null){
                taskCompletion.onTaskCompleted(jsonResponseEntity);
            }
            notifySubscribers(jsonResponseEntity, true);
        }
        else{

            if(taskCompletion != null && enableCache){
                //TODO: create a more generic naming approach
                if(getFromSolidCache()){
                    taskCompletion.onTaskCompleted(jsonResponseEntity);
                    notifySubscribers(jsonResponseEntity, true);
                }
                else{
                    errorExecution();
                }
            }
            else{
                errorExecution();
            }
        }
        if(commonTasks!=null) commonTasks.performCommonTask(result, this.getResponseStatus());
    }

    private void errorExecution(){
        boolean executed = false;

        notifySubscribers(null, false);

        if(serverTaskFailure != null && !executed){
            if(errorResponseEntityClass.getCanonicalName().equals(String.class.getCanonicalName())){
                serverTaskFailure.onServerTaskFailed((M)errorResponse, serverFailure);
            }
            else{
                serverTaskFailure.onServerTaskFailed(getErrorBody(errorResponseEntityClass, errorResponse), serverFailure);
            }
            executed = true;
        }

        if(clientTaskFailure != null && !executed){
            if(errorResponseEntityClass.getCanonicalName().equals(String.class.getCanonicalName())){
                clientTaskFailure.onClientTaskFailed((M)errorResponse, clientFailure);
            }
            else{
                clientTaskFailure.onClientTaskFailed(getErrorBody(errorResponseEntityClass, errorResponse), clientFailure);
            }

            executed = true;
        }

        if(taskFailure != null && !executed){
            try {
                if(errorResponseEntityClass.getCanonicalName().equalsIgnoreCase(Void.class.getCanonicalName())) {
                    taskFailure.onTaskFailed(null, failure);
                }
                else{
                    System.out.println("EasyRest - SAFEGUARD");
                    taskFailure.onTaskFailed(errorResponseEntityClass.newInstance(), failure);
                }

            } catch (InstantiationException e) {
                try{
                    taskFailure.onTaskFailed(null, failure);
                }
                catch(Exception e1){
                    e1.printStackTrace();
                }
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                //e.printStackTrace();
            }
            executed = true;
        }
    }

    private void createDelayedCall(boolean enableDelayedReprocess){
        GenericRestCall<T, X, M> delayedCall = new GenericRestCall(entityClass, jsonResponseEntityClass, errorResponseEntityClass);
        delayedCall.setMethodToCall(this.methodToCall);
        delayedCall.setUrl(this.getUrl());
        delayedCall.setRequestHeaders(this.getRequestHeaders());
        if(entity!=null && !entityClass.getClass().getCanonicalName().equalsIgnoreCase(Void.class.getCanonicalName())) delayedCall.setEntity(this.entity);
        delayedCall.setCacheTime(0L);
        if(!enableDelayedReprocess){
            delayedCall.setTaskCompletion(null);
            delayedCall.setTaskFailure(null);
            delayedCall.setClientTaskFailure(null);
            delayedCall.setServerTaskFailure(null);
        }
        else{
            if(taskCompletion!=null) delayedCall.setTaskCompletion(this.taskCompletion);
            if(taskFailure!=null) delayedCall.setTaskFailure(this.taskFailure);
            if(clientTaskFailure!=null) delayedCall.setClientTaskFailure(this.clientTaskFailure);
            if(serverTaskFailure!=null) delayedCall.setServerTaskFailure(this.serverTaskFailure);
        }
        delayedCall.execute(true);

    }


    public void execute(boolean asynchronously){
        if(asynchronously){
            Thread thread = new Thread(this);
            thread.start();
        }
        else{
            this.run();
        }
    }

    public <T> T getErrorBody(Class<T> errorBodyClass, String jsonBody){
        try {

            //JSONObject jsonObj = new JSONObject(jsonBody.toString());

            if(!errorBodyClass.getCanonicalName().equalsIgnoreCase(String.class.getCanonicalName())){
                T errorBody = errorBodyClass.newInstance();
                ObjectMapper mapper = new ObjectMapper();
                errorBody = mapper.readValue(jsonBody, errorBodyClass);
                return errorBody;
            }
            else{
                return (T)jsonBody;
            }

        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (JsonParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public GenericRestCall<T, X, M> getThis(){
        return this;
    }

    private void notifySubscribers(X result, boolean success){

        if(mySubscribers!=null && mySubscribers.size()>0){
            RestResults<X> results = new RestResults<>();
            results.setSubscriberEntity(result);
            results.setStatus(this.getResponseStatus().value());
            results.setSuccessful(success);

            //rx.Observable<RestResults<X>> observable = rx.Observable.just(results);
            for(Subscriber<RestResults<X>> subscriber : mySubscribers ){
                rx.Observable.just(results).subscribe(subscriber);
            }
        }

    }

    public boolean get(){
        if(EasyRest.isDebugMode()){
            if(getRequestHeaders()!=null){
                System.out.println("EasyRest - Request Headers");
                System.out.println("EasyRest - URL : " + getUrl());
                for(String s: getRequestHeaders().keySet()){
                    System.out.println("EasyRest - "+s+":"+getRequestHeaders().get(s));
                }
            }
        }

        if (this.getMethodToCall()==DefinitionsHttpMethods.METHOD_POST) {
            this.doPost();
        }

        if (this.getMethodToCall()==DefinitionsHttpMethods.METHOD_GET) {
            this.doGet();
        }

        if (this.getMethodToCall()==DefinitionsHttpMethods.METHOD_DELETE) {
            this.doDelete();
        }

        if (this.getMethodToCall()== HttpMethod.PUT) {
            this.doPut();
        }

        onPostExecute(result);

        return result;
    }

    @Override
    public void run() {

        get();
        
    }

    private void handleException(Exception e){
        failure = e;
        System.out.println("The error was caused by the body "+entityClass.getCanonicalName());
        System.out.println(" and the response " + jsonResponseEntityClass.getCanonicalName());
        System.out.println(" in the url " + url);
        e.printStackTrace();
        this.result = false;
    }

    private <T extends HttpStatusCodeException> void handleException(T e){
        this.responseStatus = e.getStatusCode();
        System.out.println("BAD:" + e.getResponseBodyAsString());
        errorResponse = e.getResponseBodyAsString();
        failure = e;
        System.out.println("The error was caused by the body "+entityClass.getCanonicalName());
        System.out.println(" and the response " + jsonResponseEntityClass.getCanonicalName());
        System.out.println(" in the url " + url);
        System.out.println(" with the response " + errorResponse);
        e.printStackTrace();
        this.result = false;
        if(e.getClass().getCanonicalName().equalsIgnoreCase(HttpClientErrorException.class.getCanonicalName())){
            //errorType = CLIENT_ERROR;
            clientFailure = (HttpClientErrorException) e;
        }
        if(e.getClass().getCanonicalName().equalsIgnoreCase(HttpServerErrorException.class.getCanonicalName())){
            //errorType = SERVER_ERROR;
            serverFailure = (HttpServerErrorException) e;
        }
    }

    private Object byteToObject(byte[] data){
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = null;
        try {
            is = new ObjectInputStream(in);
            return is.readObject();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }

    }

}
