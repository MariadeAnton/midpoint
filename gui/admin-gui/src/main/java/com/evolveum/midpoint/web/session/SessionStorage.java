/*
 * Copyright (c) 2010-2013 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.web.session;

import com.evolveum.midpoint.web.component.breadcrumbs.Breadcrumb;
import org.apache.commons.lang.Validate;
import org.apache.wicket.Page;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import java.io.Serializable;
import java.util.*;

/**
 * @author lazyman
 */
public class SessionStorage implements Serializable {

    private List<Breadcrumb> breadcrumbs;

    /**
     * place to store "previous page" for back button
     */
    private Class<? extends WebPage> previousPage;
    
    private Page previousPageInstance;
    /**
     * place to store "previous page" parameters for back button
     */
    private PageParameters previousPageParams;

    /**
     * place to store information in session for various pages
     */
    private Map<String, PageStorage> pageStorageMap = new HashMap<>();

    public static final String KEY_CONFIGURATION = "configuration";
    public static final String KEY_USERS = "users";
    public static final String KEY_REPORTS = "reports";
    public static final String KEY_RESOURCES = "resources";
    public static final String KEY_ROLES = "roles";
    public static final String KEY_SERVICES = "services";
    public static final String KEY_ROLE_MEMBERS = "roleMembers";
    
    private static final String KEY_TASKS = "tasks";

    /**
    *   Store session information for user preferences about paging size in midPoint GUI
    * */
    private UserProfileStorage userProfile;

    public Class<? extends WebPage> getPreviousPage() {
        return previousPage;
    }

    public void setPreviousPage(Class<? extends WebPage> previousPage) {
        this.previousPage = previousPage;
    }
    
    public void setPreviousPageInstance(Page previousPage) {
        this.previousPageInstance = previousPage;
    }
    
    public Page getPreviousPageInstance() {
		return previousPageInstance;
	}

    public PageParameters getPreviousPageParams() {
        return previousPageParams;
    }

    public void setPreviousPageParams(PageParameters previousPageParams) {
        this.previousPageParams = previousPageParams;
    }
    
    public Map<String, PageStorage> getPageStorageMap() {
		return pageStorageMap;
	}

    public ConfigurationStorage getConfiguration() {
        if (pageStorageMap.get(KEY_CONFIGURATION) == null) {
            pageStorageMap.put(KEY_CONFIGURATION, new ConfigurationStorage());
        }
        return (ConfigurationStorage)pageStorageMap.get(KEY_CONFIGURATION);
    }

    public UsersStorage getUsers() {
        if (pageStorageMap.get(KEY_USERS) == null) {
            pageStorageMap.put(KEY_USERS, new UsersStorage());
        }
        return (UsersStorage)pageStorageMap.get(KEY_USERS);
    }

    public ResourcesStorage getResources() {
        if (pageStorageMap.get(KEY_RESOURCES) == null) {
            pageStorageMap.put(KEY_RESOURCES, new ResourcesStorage());
        }
        return (ResourcesStorage)pageStorageMap.get(KEY_RESOURCES);
    }

    public RolesStorage getRoles() {
        if (pageStorageMap.get(KEY_ROLES) == null) {
            pageStorageMap.put(KEY_ROLES, new RolesStorage());
        }
        return (RolesStorage)pageStorageMap.get(KEY_ROLES);
    }
    
    public ServicesStorage getServices() {
        if (pageStorageMap.get(KEY_SERVICES) == null) {
            pageStorageMap.put(KEY_SERVICES, new ServicesStorage());
        }
        return (ServicesStorage)pageStorageMap.get(KEY_SERVICES);
    }
    
    public RoleMembersStorage getRoleMembers() {
    	if (pageStorageMap.get(KEY_ROLE_MEMBERS) == null) {
            pageStorageMap.put(KEY_ROLE_MEMBERS, new RoleMembersStorage());
        }
        return (RoleMembersStorage)pageStorageMap.get(KEY_ROLE_MEMBERS);
    }

    public TasksStorage getTasks() {
        if (pageStorageMap.get(KEY_TASKS) == null) {
            pageStorageMap.put(KEY_TASKS, new TasksStorage());
        }
        return (TasksStorage)pageStorageMap.get(KEY_TASKS);
    }

    public ReportsStorage getReports() {
        if (pageStorageMap.get(KEY_REPORTS) == null) {
            pageStorageMap.put(KEY_REPORTS, new ReportsStorage());
        }
        return (ReportsStorage)pageStorageMap.get(KEY_REPORTS);
    }

    public UserProfileStorage getUserProfile(){
        if(userProfile == null){
            userProfile = new UserProfileStorage();
        }
        return userProfile;
    }
    
    public PageStorage initPageStorage(String key){
    	PageStorage pageStorage = null;
    	if (KEY_USERS.equals(key)){
    		pageStorage = new UsersStorage();
    		pageStorageMap.put(KEY_USERS, pageStorage);
    		
    	} else if (KEY_ROLES.equals(key)){
    		pageStorage = new RolesStorage();
    		pageStorageMap.put(KEY_ROLES, pageStorage);
    	} else if (KEY_SERVICES.equals(key)) {
    		pageStorage = new ServicesStorage();
    		pageStorageMap.put(KEY_SERVICES, pageStorage);
    	}
    	return pageStorage;
    	//TODO: fixme
    }

    public void setUserProfile(UserProfileStorage profile){
        userProfile = profile;
    }

    public List<Breadcrumb> getBreadcrumbs() {
        if (breadcrumbs == null) {
            breadcrumbs = new Stack<>();
        }
        return breadcrumbs;
    }

    public void pushBreadcrumb(Breadcrumb breadcrumb) {
        Validate.notNull(breadcrumb, "Breadcrumb must not be null");

        Breadcrumb last = getBreadcrumbs().isEmpty() ?
                null : getBreadcrumbs().get(getBreadcrumbs().size() - 1);
        if (last != null && last.equals(breadcrumb)) {
            return;
        }

        getBreadcrumbs().add(breadcrumb);
    }

    public Breadcrumb popBreadcrumb() {
        if (getBreadcrumbs().isEmpty()) {
            return null;
        }

        return getBreadcrumbs().remove(getBreadcrumbs().size() - 1);
    }

    public Breadcrumb peekBreadcrumb() {
        if (getBreadcrumbs().isEmpty()) {
            return null;
        }

        return getBreadcrumbs().get(getBreadcrumbs().size() - 1);
    }

    public void clearBreadcrumbs() {
        getBreadcrumbs().clear();
    }
}
