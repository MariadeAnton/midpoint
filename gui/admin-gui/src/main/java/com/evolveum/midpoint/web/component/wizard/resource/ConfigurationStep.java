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

package com.evolveum.midpoint.web.component.wizard.resource;

import com.evolveum.midpoint.gui.api.model.LoadableModel;
import com.evolveum.midpoint.gui.api.page.PageBase;
import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.gui.api.util.WebModelServiceUtils;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.prism.ItemDefinition;
import com.evolveum.midpoint.prism.PrismContainer;
import com.evolveum.midpoint.prism.PrismContainerDefinition;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.ConnectorTypeUtil;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.util.exception.ObjectNotFoundException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.exception.SystemException;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.web.component.AjaxSubmitButton;
import com.evolveum.midpoint.web.component.TabbedPanel;
import com.evolveum.midpoint.web.component.data.TablePanel;
import com.evolveum.midpoint.web.component.data.column.ColumnTypeDto;
import com.evolveum.midpoint.web.component.data.column.ColumnUtils;
import com.evolveum.midpoint.web.component.prism.ContainerStatus;
import com.evolveum.midpoint.web.component.prism.ContainerWrapper;
import com.evolveum.midpoint.web.component.prism.ContainerWrapperFactory;
import com.evolveum.midpoint.web.component.prism.PrismContainerPanel;
import com.evolveum.midpoint.web.component.util.ListDataProvider;
import com.evolveum.midpoint.web.component.wizard.WizardStep;
import com.evolveum.midpoint.web.page.admin.resources.PageResourceWizard;
import com.evolveum.midpoint.web.page.admin.resources.dto.TestConnectionResultDto;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ConnectorType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.util.ListModel;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author lazyman
 * @author mederly
 */
public class ConfigurationStep extends WizardStep {

	private static final Trace LOGGER = TraceManager.getTrace(ConfigurationStep.class);

    private static final String DOT_CLASS = ConfigurationStep.class.getName() + ".";
    private static final String TEST_CONNECTION = DOT_CLASS + "testConnection";
    private static final String OPERATION_SAVE = DOT_CLASS + "saveResource";

    private static final String ID_CONFIGURATION = "configuration";
    private static final String ID_TEST_CONNECTION = "testConnection";
	private static final String ID_MAIN = "main";

	final private LoadableModel<PrismObject<ResourceType>> resourceModelNoFetch;
	final private LoadableModel<List<ContainerWrapper>> configurationPropertiesModel;
	final private PageResourceWizard parentPage;

    public ConfigurationStep(LoadableModel<PrismObject<ResourceType>> modelNoFetch, final PageResourceWizard parentPage) {
        super(parentPage);
        this.resourceModelNoFetch = modelNoFetch;
		this.parentPage = parentPage;

        this.configurationPropertiesModel = new LoadableModel<List<ContainerWrapper>>(false) {
            @Override
            protected List<ContainerWrapper> load() {
				return createConfigContainerWrappers();
            }
		};

        initLayout();
    }

	@NotNull
	private List<ContainerWrapper> createConfigContainerWrappers() {
		PrismObject<ResourceType> resource = resourceModelNoFetch.getObject();
		PrismContainer<ConnectorConfigurationType> configuration = resource.findContainer(ResourceType.F_CONNECTOR_CONFIGURATION);

		List<ContainerWrapper> containerWrappers = new ArrayList<>();

		if (configuration == null) {
			PrismObject<ConnectorType> connector = ResourceTypeUtil.getConnectorIfPresent(resource);
			if (connector == null) {
				throw new IllegalStateException("No resolved connector object in resource object");
			}
			ConnectorType connectorType = connector.asObjectable();
			PrismSchema schema;
			try {
				schema = ConnectorTypeUtil.parseConnectorSchema(connectorType, parentPage.getPrismContext());
			} catch (SchemaException e) {
				throw new SystemException("Couldn't parse connector schema: " + e.getMessage(), e);
			}
			PrismContainerDefinition<ConnectorConfigurationType> definition = ConnectorTypeUtil.findConfigurationContainerDefinition(connectorType, schema);
			// Fixing (errorneously) set maxOccurs = unbounded. See MID-2317 and related issues.
			PrismContainerDefinition<ConnectorConfigurationType> definitionFixed = definition.clone();
			definitionFixed.setMaxOccurs(1);
			configuration = definitionFixed.instantiate();
		}

		List<PrismContainerDefinition> containerDefinitions = getSortedConfigContainerDefinitions(configuration);
		for (PrismContainerDefinition containerDef : containerDefinitions) {
			ItemPath containerPath = new ItemPath(ResourceType.F_CONNECTOR_CONFIGURATION, containerDef.getName());
			PrismContainer container = configuration.findContainer(containerDef.getName());

			ContainerWrapperFactory cwf = new ContainerWrapperFactory(parentPage);
			ContainerWrapper containerWrapper;
			if (container != null) {
				containerWrapper = cwf.createContainerWrapper(container, ContainerStatus.MODIFYING, containerPath, false);
			} else {
				container = containerDef.instantiate();
				containerWrapper = cwf.createContainerWrapper(container, ContainerStatus.ADDING, containerPath, false);
			}
			containerWrappers.add(containerWrapper);
		}
		return containerWrappers;
	}

	@NotNull
	private List<PrismContainerDefinition> getSortedConfigContainerDefinitions(PrismContainer<ConnectorConfigurationType> configuration) {
		List<PrismContainerDefinition> relevantDefinitions = new ArrayList<>();
		for (ItemDefinition<?> def : configuration.getDefinition().getDefinitions()) {
			if (def instanceof PrismContainerDefinition) {
				relevantDefinitions.add((PrismContainerDefinition) def);
			}
		}
		Collections.sort(relevantDefinitions, new Comparator<PrismContainerDefinition>() {
			@Override
			public int compare(PrismContainerDefinition o1, PrismContainerDefinition o2) {
				int ord1 = o1.getDisplayOrder() != null ? o1.getDisplayOrder() : Integer.MAX_VALUE;
				int ord2 = o2.getDisplayOrder() != null ? o2.getDisplayOrder() : Integer.MAX_VALUE;
				return Integer.compare(ord1, ord2);
			}
		});
		return relevantDefinitions;
	}

	@Override
	protected void onConfigure() {
		configurationPropertiesModel.reset();
		updateConfigurationTabs();
	}

	private void initLayout() {
    	com.evolveum.midpoint.web.component.form.Form form = new com.evolveum.midpoint.web.component.form.Form<>(ID_MAIN, true);
        form.setOutputMarkupId(true);
        add(form);

		form.add(WebComponentUtil.createTabPanel(ID_CONFIGURATION, parentPage, new ArrayList<ITab>(), null));

		AjaxSubmitButton testConnection = new AjaxSubmitButton(ID_TEST_CONNECTION,
                createStringResource("ConfigurationStep.button.testConnection")) {

            @Override
            protected void onError(final AjaxRequestTarget target, Form<?> form) {
                WebComponentUtil.refreshFeedbacks(form, target);
            }

            @Override
            protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
                testConnectionPerformed(target);
            }
        };
        add(testConnection);
    }

	private void updateConfigurationTabs() {
		final com.evolveum.midpoint.web.component.form.Form form = getForm();
		TabbedPanel<ITab> tabbedPanel = getConfigurationTabbedPanel();
		List<ITab> tabs = tabbedPanel.getTabs().getObject();
		tabs.clear();

		List<ContainerWrapper> wrappers = configurationPropertiesModel.getObject();
		for (final ContainerWrapper wrapper : wrappers) {
			String tabName = getString(wrapper.getDisplayName(), null, wrapper.getDisplayName());
			tabs.add(new AbstractTab(new Model<>(tabName)) {
				@Override
				public WebMarkupContainer getPanel(String panelId) {
					return new PrismContainerPanel(panelId, new Model<>(wrapper), true, form, parentPage);
				}
			});
		}
	}

	@SuppressWarnings("unchecked")
	private com.evolveum.midpoint.web.component.form.Form getForm() {
		return (com.evolveum.midpoint.web.component.form.Form) get(ID_MAIN);
	}

	@SuppressWarnings("unchecked")
	public TabbedPanel<ITab> getConfigurationTabbedPanel() {
		return (TabbedPanel<ITab>) get(createComponentPath(ID_MAIN, ID_CONFIGURATION));
	}

	// copied from PageResource, TODO deduplicate
	private void testConnectionPerformed(AjaxRequestTarget target) {
		saveChanges();

		PageBase page = getPageBase();
		ModelService model = page.getModelService();

		OperationResult result = new OperationResult(TEST_CONNECTION);
		List<TestConnectionResultDto> resultDtoList = new ArrayList<>();
		try {
			Task task = page.createSimpleTask(TEST_CONNECTION);
			String oid = resourceModelNoFetch.getObject().getOid();
			result = model.testResource(oid, task);
			resultDtoList = TestConnectionResultDto.getResultDtoList(result, this);
		} catch (ObjectNotFoundException ex) {
			result.recordFatalError("Failed to test resource connection", ex);
		}

		page.setMainPopupContent(createConnectionResultTable(new ListModel<>(resultDtoList)));
		page.getMainPopup().setInitialHeight(400);
		page.getMainPopup().setInitialWidth(600);
		page.showMainPopup(target);

		page.showResult(result, "Test connection failed", false);
		target.add(page.getFeedbackPanel());
		target.add(getForm());
	}

	private TablePanel<TestConnectionResultDto> createConnectionResultTable(ListModel<TestConnectionResultDto> model) {
		ListDataProvider<TestConnectionResultDto> listprovider = new ListDataProvider<>(this,
				model);
		List<ColumnTypeDto> columns = Arrays.asList(new ColumnTypeDto<String>("Operation Name", "operationName", null),
				new ColumnTypeDto("Status", "status", null),
				new ColumnTypeDto<String>("Error Message", "errorMessage", null));

		TablePanel<TestConnectionResultDto> table =
				new TablePanel<>(getPageBase().getMainPopupBodyId(), listprovider, ColumnUtils.<TestConnectionResultDto>createColumns(columns));
		table.setOutputMarkupId(true);
		return table;
	}


	@Override
    public void applyState() {
        saveChanges();
    }

    private void saveChanges() {
        Task task = parentPage.createSimpleTask(OPERATION_SAVE);
        OperationResult result = task.getResult();
        try {
            List<ContainerWrapper> wrappers = configurationPropertiesModel.getObject();
			ObjectDelta delta = ObjectDelta.createEmptyModifyDelta(ResourceType.class, parentPage.getEditedResourceOid(), parentPage.getPrismContext());
			for (ContainerWrapper wrapper : wrappers) {
				wrapper.collectModifications(delta);
			}
			LOGGER.info("Applying delta:\n{}", delta.debugDump());
			WebModelServiceUtils.save(delta, result, parentPage);

			parentPage.resetModels();
        } catch (Exception ex) {
            LoggingUtils.logException(LOGGER, "Error occurred during resource test connection", ex);
            result.recordFatalError("Couldn't save configuration changes.", ex);
        } finally {
            result.computeStatusIfUnknown();
            setResult(result);
        }

        if (WebComponentUtil.showResultInPage(result)) {
            parentPage.showResult(result);
        }

		configurationPropertiesModel.reset();
		updateConfigurationTabs();
		TabbedPanel<ITab> tabbedPanel = getConfigurationTabbedPanel();
		tabbedPanel.setSelectedTab(tabbedPanel.getSelectedTab());
	}
}
