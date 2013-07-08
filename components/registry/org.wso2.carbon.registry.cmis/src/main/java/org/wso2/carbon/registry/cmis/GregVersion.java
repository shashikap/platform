/*
 * Copyright (c) 2006, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.registry.cmis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.Resource;
import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.cmis.impl.DocumentTypeHandler;
import org.wso2.carbon.registry.cmis.impl.GregProperty;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instances of this class represent a specific version of a cmis:document backed by an underlying
 * GREG <code>Node</code>.
 */
public class GregVersion extends GregVersionBase {
    private static final Pattern VERSION_LABEL_PATTERN = Pattern.compile("(\\d+)(\\.(\\d+))?.*");
    private static final int GROUP_MAJOR = 1;
    private static final int GROUP_MINOR = 3;
    private static final Logger log = LoggerFactory.getLogger(GregVersion.class);
    private final String version;

    public GregVersion(Registry repository,Resource node, String version, GregTypeManager typeManager, PathManager pathManager) {

        //TODO
        //I might have to load the new version given in "version" and then make the node
        super(repository, node, typeManager, pathManager);
        this.version = version;
    }

    //------------------------------------------< protected >---

    @Override
    protected Resource getContextNode() throws RegistryException {
        return getNode();
    }
    
    @Override
    protected String getObjectId() throws RegistryException {
        return version;
    }

    @Override
    protected boolean isLatestVersion() throws RegistryException {
        String[] versions = getRepository().getVersions(getNode().getPath());
        if(versions!=null){
            return versions[0].equals(version);
        }
        else{
            return getNode().getPath().equals(version);
        }
    }

    @Override
    protected boolean isMajorVersion() {
        String property = getNode().getProperty(GregProperty.GREG_VERSION_STATE);
        if(property != null){
            if(property.equals(GregProperty.GREG_MAJOR_VERSION)){
                return true;
            }
            else{
                return false;
            }
        }
        else{
            return false;
        }
    }

    @Override
    protected boolean isLatestMajorVersion() throws RegistryException {
        return isLatestVersion();
    }
    
    
    /*
     * TODO
     * 
     * */
    @Override
    protected String getVersionLabel() throws RegistryException {
        String name = version;
        String major = parseVersion(name, GROUP_MINOR);

        return major == null
                ? name
                : (Integer.parseInt(major) + 1) + ".0";
    }

    @Override
    protected String getCheckInComment() throws RegistryException {
        // todo set checkinComment
        return "";
    }

    //------------------------------------------< private >---

    private static String parseVersion(String name, int group) {
        Matcher matcher = VERSION_LABEL_PATTERN.matcher(name);
        return matcher.matches()
                ? matcher.group(group)
                : null;
    }

	@Override
	protected GregObject create(Resource resource) {
		
		DocumentTypeHandler documentTypeHandler = new DocumentTypeHandler(getRepository(), pathManager, typeManager);
		try {
			return documentTypeHandler.getGregNode(resource);
		} catch (RegistryException e) {
			log.debug(e.getMessage(), e);
		}
		return null;
		
	}


}
