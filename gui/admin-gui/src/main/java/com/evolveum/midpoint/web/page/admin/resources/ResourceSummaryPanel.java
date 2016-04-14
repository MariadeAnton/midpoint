/**
 * Copyright (c) 2016 Evolveum
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
package com.evolveum.midpoint.web.page.admin.resources;

import javax.xml.namespace.QName;

import com.evolveum.midpoint.web.component.AbstractSummaryPanel;
import org.apache.wicket.Component;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.IModel;

import com.evolveum.midpoint.gui.api.GuiStyleConstants;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.schema.util.ResourceTypeUtil;
import com.evolveum.midpoint.web.component.ObjectSummaryPanel;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ResourceType;

public class ResourceSummaryPanel extends ObjectSummaryPanel<ResourceType>{
	private static final long serialVersionUID = 1L;

	public ResourceSummaryPanel(String id, IModel<PrismObject<ResourceType>> model) {
		super(id, model);
		
		boolean down = ResourceTypeUtil.isDown(model.getObject().asObjectable());
		Label summaryTag  = new Label(ID_FIRST_SUMMARY_TAG, down ? "DOWN" : "UP");
		addTag(summaryTag);
	}
	
	@Override
	protected String getIconCssClass() {
		return GuiStyleConstants.CLASS_OBJECT_RESOURCE_ICON;
	}

	@Override
	protected String getIconBoxAdditionalCssClass() {
		return "summary-panel-resource";
	}

	@Override
	protected String getBoxAdditionalCssClass() {
		return "summary-panel-resource";
	}

	@Override
	protected QName getDisplayNamePropertyName() {
		return ResourceType.F_NAME;
	}

}
