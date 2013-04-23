/*
 * Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.esb.endpoint.test;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.esb.ESBIntegrationTest;

/**
 * CARBON-11016
 */
public class CARBON11016TestCase extends ESBIntegrationTest {

    @BeforeClass(alwaysRun = true)
    public void beforeClass() throws Exception {
        init();
    }

    @Test(groups = {"wso2.esb"}, description = "Patch : CARBON-11016 : Updating the synapse configuration with end point which has set the timeout duration makes synapse configuration invalid.")
    public void testSetTimeoutDurationInEndpoints() throws Exception {
        String filePath = "/artifacts/ESB/synapseconfig/patch_automation/CARBON11016_synapse.xml";
        loadESBConfigurationFromClasspath(filePath);
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() throws Exception {
        cleanup();
    }

}
