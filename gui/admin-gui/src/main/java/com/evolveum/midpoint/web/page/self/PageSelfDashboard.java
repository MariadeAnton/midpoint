/*
 * Copyright (c) 2010-2015 Evolveum
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
package com.evolveum.midpoint.web.page.self;

import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.builder.QueryBuilder;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.security.api.AuthorizationConstants;
import com.evolveum.midpoint.security.api.MidPointPrincipal;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.application.AuthorizationAction;
import com.evolveum.midpoint.web.application.PageDescriptor;
import com.evolveum.midpoint.web.component.SecurityContextAwareCallable;
import com.evolveum.midpoint.web.component.breadcrumbs.Breadcrumb;
import com.evolveum.midpoint.web.component.util.CallableResult;
import com.evolveum.midpoint.web.component.util.ListDataProvider;
import com.evolveum.midpoint.web.component.util.VisibleEnableBehaviour;
import com.evolveum.midpoint.web.component.wf.WorkItemsPanel;
import com.evolveum.midpoint.web.page.admin.home.component.AsyncDashboardPanel;
import com.evolveum.midpoint.web.page.admin.home.component.DashboardColor;
import com.evolveum.midpoint.web.page.admin.home.dto.AccountCallableResult;
import com.evolveum.midpoint.web.page.admin.workflow.ProcessInstancesPanel;
import com.evolveum.midpoint.web.page.admin.workflow.dto.ProcessInstanceDto;
import com.evolveum.midpoint.web.page.admin.workflow.dto.ProcessInstanceDtoProvider;
import com.evolveum.midpoint.web.page.admin.workflow.dto.WorkItemDto;
import com.evolveum.midpoint.web.page.self.component.DashboardSearchPanel;
import com.evolveum.midpoint.web.page.self.component.LinksPanel;
import com.evolveum.midpoint.web.security.SecurityUtils;
import com.evolveum.midpoint.xml.ns._public.common.common_3.RichHyperlinkType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.WorkItemType;
import org.apache.commons.lang.Validate;
import org.apache.wicket.Application;
import org.apache.wicket.Component;
import org.apache.wicket.Session;
import org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.PropertyModel;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.List;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.WorkItemType.F_WORK_ITEM_CREATED_TIMESTAMP;

/**
 * @author Viliam Repan (lazyman)
 * @author Kate Honchar
 */
@PageDescriptor(url = {"/self/dashboard", "/self"}, action = {
        @AuthorizationAction(actionUri = PageSelf.AUTH_SELF_ALL_URI,
                label = PageSelf.AUTH_SELF_ALL_LABEL,
                description = PageSelf.AUTH_SELF_ALL_DESCRIPTION),
        @AuthorizationAction(actionUri = AuthorizationConstants.AUTZ_UI_SELF_DASHBOARD_URL,
                label = "PageSelfDashboard.auth.dashboard.label",
                description = "PageSelfDashboard.auth.dashboard.description")})
public class PageSelfDashboard extends PageSelf {
    private static final Trace LOGGER = TraceManager.getTrace(PageSelfDashboard.class);

    private static final String ID_LINKS_PANEL = "linksPanel";
    private static final String ID_WORK_ITEMS_PANEL = "workItemsPanel";
    private static final String ID_SEARCH_PANEL = "searchPanel";
    private static final String ID_REQUESTS_PANEL = "requestPanel";
    private static final String DOT_CLASS = PageSelfDashboard.class.getName() + ".";
    private static final String OPERATION_LOAD_WORK_ITEMS = DOT_CLASS + "loadWorkItems";
    private static final String OPERATION_LOAD_REQUESTS = DOT_CLASS + "loadRequests";
    private static final int MAX_WORK_ITEMS = 1000;
    private static final int MAX_REQUESTS = 1000;
    private final Model<PrismObject<UserType>> principalModel = new Model<PrismObject<UserType>>();
    private static final String OPERATION_LOAD_USER = DOT_CLASS + "loadUser";
    private static final String OPERATION_GET_SYSTEM_CONFIG = DOT_CLASS + "getSystemConfiguration";

    public PageSelfDashboard() {
        principalModel.setObject(loadUser());
        setTimeZone(PageSelfDashboard.this);
        initLayout();
    }

	private transient Application application;

    @Override
    protected void createBreadcrumb() {
        super.createBreadcrumb();

        Breadcrumb bc = getSessionStorage().peekBreadcrumb();
        bc.setIcon(new Model("fa fa-dashboard"));
    }

    private void initLayout(){
        DashboardSearchPanel dashboardSearchPanel = new DashboardSearchPanel(ID_SEARCH_PANEL, null);
        add(dashboardSearchPanel);
        if (! WebComponentUtil.isAuthorized(AuthorizationConstants.AUTZ_UI_USERS_ALL_URL,
                AuthorizationConstants.AUTZ_UI_USERS_URL, AuthorizationConstants.AUTZ_UI_RESOURCES_ALL_URL,
                AuthorizationConstants.AUTZ_UI_RESOURCES_URL, AuthorizationConstants.AUTZ_UI_TASKS_ALL_URL,
                AuthorizationConstants.AUTZ_UI_TASKS_URL)) {
            dashboardSearchPanel.setVisible(false);
        }
        LinksPanel linksPanel = new LinksPanel(ID_LINKS_PANEL, new IModel<List<RichHyperlinkType>>() {
            @Override
            public List<RichHyperlinkType> getObject() {
                return loadLinksList();
            }

            @Override
            public void setObject(List<RichHyperlinkType> richHyperlinkTypes) {

            }

            @Override
            public void detach() {

            }
        });
        add(linksPanel);

		// TODO is this correct? [med]
		application = getApplication();
		final Session session = Session.get();

		AsyncDashboardPanel<Object, List<WorkItemDto>> workItemsPanel =
                new AsyncDashboardPanel<Object, List<WorkItemDto>>(ID_WORK_ITEMS_PANEL, createStringResource("PageSelfDashboard.workItems"),
                        "fa fa-fw fa-tasks", DashboardColor.RED) {

                    @Override
                    protected SecurityContextAwareCallable<CallableResult<List<WorkItemDto>>> createCallable(
                            Authentication auth, IModel callableParameterModel) {

                        return new SecurityContextAwareCallable<CallableResult<List<WorkItemDto>>>(
                                getSecurityEnforcer(), auth) {

                            @Override
                            public CallableResult<List<WorkItemDto>> callWithContextPrepared() throws Exception {
								setupContext(application, session);	// TODO is this correct? [med]
                                return loadWorkItems();
                            }
                        };
                    }

                    @Override
                    protected Component getMainComponent(String markupId) {
						ISortableDataProvider provider = new ListDataProvider(this, new PropertyModel<List<WorkItemDto>>(getModel(), CallableResult.F_VALUE));
						return new WorkItemsPanel(markupId, provider, null, 10, WorkItemsPanel.View.DASHBOARD);
                    }
                };

        workItemsPanel.add(new VisibleEnableBehaviour() {
            @Override
            public boolean isVisible() {
                return getWorkflowManager().isEnabled();
            }
        });
        add(workItemsPanel);

        AsyncDashboardPanel<Object, List<ProcessInstanceDto>> myRequestsPanel =
                new AsyncDashboardPanel<Object, List<ProcessInstanceDto>>(ID_REQUESTS_PANEL, createStringResource("PageSelfDashboard.myRequests"),
                        "fa fa-fw fa-pencil-square-o", DashboardColor.GREEN) {

                    @Override
                    protected SecurityContextAwareCallable<CallableResult<List<ProcessInstanceDto>>> createCallable(
                            Authentication auth, IModel callableParameterModel) {

                        return new SecurityContextAwareCallable<CallableResult<List<ProcessInstanceDto>>>(
                                getSecurityEnforcer(), auth) {

                            @Override
                            public CallableResult<List<ProcessInstanceDto>> callWithContextPrepared() throws Exception {
								setupContext(application, session);
                                return loadMyRequests();
                            }
                        };
                    }

                    @Override
                    protected Component getMainComponent(String markupId) {
						ISortableDataProvider provider = new ListDataProvider(this, new PropertyModel<List<ProcessInstanceDto>>(getModel(), CallableResult.F_VALUE));
                        return new ProcessInstancesPanel(markupId, provider, null, 10, ProcessInstancesPanel.View.DASHBOARD, null);
                    }
                };

        myRequestsPanel.add(new VisibleEnableBehaviour() {
            @Override
            public boolean isVisible() {
                return getWorkflowManager().isEnabled();
            }
        });
        add(myRequestsPanel);

    }

    private CallableResult<List<WorkItemDto>> loadWorkItems() {

        LOGGER.debug("Loading work items.");

        AccountCallableResult callableResult = new AccountCallableResult();
        List<WorkItemDto> list = new ArrayList<>();
        callableResult.setValue(list);

        if (!getWorkflowManager().isEnabled()) {
            return callableResult;
        }

        PrismObject<UserType> user = principalModel.getObject();
        if (user == null) {
            return callableResult;
        }

        Task task = createSimpleTask(OPERATION_LOAD_WORK_ITEMS);
        OperationResult result = task.getResult();
        callableResult.setResult(result);

        try {
            ObjectQuery query = QueryBuilder.queryFor(WorkItemType.class, getPrismContext())
                    .item(WorkItemType.F_ASSIGNEE_REF).ref(user.getOid())
                    .desc(F_WORK_ITEM_CREATED_TIMESTAMP)
                    .build();
            List<WorkItemType> workItems = getModelService().searchContainers(WorkItemType.class, query, null, task, result);
            for (WorkItemType workItem : workItems) {
                list.add(new WorkItemDto(workItem));
            }
        } catch (Exception e) {
            result.recordFatalError("Couldn't get list of work items.", e);
        }

        result.recordSuccessIfUnknown();
        result.recomputeStatus();

        LOGGER.debug("Finished work items loading.");

        return callableResult;
    }

    private CallableResult<List<ProcessInstanceDto>> loadMyRequests() {

        LOGGER.debug("Loading requests.");

        AccountCallableResult<List<ProcessInstanceDto>> callableResult = new AccountCallableResult<>();
        List<ProcessInstanceDto> list = new ArrayList<ProcessInstanceDto>();
        callableResult.setValue(list);

        if (!getWorkflowManager().isEnabled()) {
            return callableResult;
        }

		ProcessInstanceDtoProvider provider = new ProcessInstanceDtoProvider(this, true, false);
		provider.iterator(0, Integer.MAX_VALUE);
		callableResult.setValue(provider.getAvailableData());

        LOGGER.debug("Finished requests loading.");

        return callableResult;
    }


    private PrismObject<UserType> loadUser() {
        MidPointPrincipal principal = SecurityUtils.getPrincipalUser();
        Validate.notNull(principal, "No principal");
        if (principal.getOid() == null) {
            throw new IllegalArgumentException("No OID in principal: "+principal);
        }

        Task task = createSimpleTask(OPERATION_LOAD_USER);
        OperationResult result = task.getResult();
        PrismObject<UserType> user = WebModelServiceUtils.loadObject(UserType.class,
                principal.getOid(), PageSelfDashboard.this, task, result);
        result.computeStatus();

        if (!WebComponentUtil.isSuccessOrHandledError(result)) {
            showResult(result);
        }

        return user;
    }

    private List<RichHyperlinkType> loadLinksList() {
        PrismObject<UserType> user = principalModel.getObject();
        if (user == null) {
            return new ArrayList<RichHyperlinkType>();
        } else {
            return ((PageBase)getPage()).loadAdminGuiConfiguration().getUserDashboardLink();
        }
    }

}
