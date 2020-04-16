package de.mhus.osgi.services.shiro;

import org.apache.shiro.util.Factory;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * TODO - Class JavaDoc
 * @param <T> 
 *
 * @since 1.0
 */
public abstract class MyAbstractFactory<T> implements Factory<T> {

    private boolean singleton;
    private T singletonInstance;

    public MyAbstractFactory() {
        this.singleton = true;
    }

    public boolean isSingleton() {
        return singleton;
    }

    public void setSingleton(boolean singleton) {
        this.singleton = singleton;
    }

    @Override
    public T getInstance() {
        T instance;
        if (isSingleton()) {
            if (this.singletonInstance == null) {
                this.singletonInstance = createInstance();
            }
            instance = this.singletonInstance;
        } else {
            instance = createInstance();
        }
        if (instance == null) {
            String msg = "Factory 'createInstance' implementation returned a null object.";
            throw new IllegalStateException(msg);
        }
        return instance;
    }

    protected abstract T createInstance();
}