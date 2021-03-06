package com.grizzly.rest.Model;

import org.springframework.web.client.HttpServerErrorException;

/**
 * Implement this interface in the summoner class to perform any fallback operations when the rest call fails.
 * Created by Fco Pardo on 8/24/14.
 */
public interface afterServerTaskFailure<T> {
    void onServerTaskFailed(T result, HttpServerErrorException e);
}
