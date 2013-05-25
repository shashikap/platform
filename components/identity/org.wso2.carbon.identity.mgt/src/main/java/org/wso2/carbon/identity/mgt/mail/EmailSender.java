/*
 * Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.mgt.mail;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * email sender this creates a new task in thread pool for each email sending request
 */
public class EmailSender  {

    EmailSendingModule module;

    private static ExecutorService threadPool = Executors.newFixedThreadPool(5);
    

    /**
     *  creates and submits a task to the thread pool
     *
     * @param module  email sending module as task
     */
    public void sendEmail(EmailSendingModule module) {

        threadPool.submit(module);
    }

    public EmailSendingModule getModule() {
        return module;
    }
}
