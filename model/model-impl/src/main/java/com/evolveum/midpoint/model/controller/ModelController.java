/*
 * Copyright (c) 2011 Evolveum
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://www.opensource.org/licenses/cddl1 or
 * CDDLv1.0.txt file in the source code distribution.
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 *
 * Portions Copyrighted 2011 [name of copyright owner]
 */
package com.evolveum.midpoint.model.controller;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.evolveum.midpoint.api.logging.LoggingUtils;
import com.evolveum.midpoint.api.logging.Trace;
import com.evolveum.midpoint.common.DebugUtil;
import com.evolveum.midpoint.common.Utils;
import com.evolveum.midpoint.common.jaxb.JAXBUtil;
import com.evolveum.midpoint.common.object.ObjectTypeUtil;
import com.evolveum.midpoint.common.patch.PatchXml;
import com.evolveum.midpoint.common.result.OperationResult;
import com.evolveum.midpoint.logging.TraceManager;
import com.evolveum.midpoint.provisioning.api.ProvisioningService;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.ObjectTypes;
import com.evolveum.midpoint.schema.ProvisioningTypes;
import com.evolveum.midpoint.schema.exception.CommunicationException;
import com.evolveum.midpoint.schema.exception.ObjectAlreadyExistsException;
import com.evolveum.midpoint.schema.exception.ObjectNotFoundException;
import com.evolveum.midpoint.schema.exception.SchemaException;
import com.evolveum.midpoint.schema.exception.SystemException;
import com.evolveum.midpoint.util.DOMUtil;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountConstructionType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.AccountShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectReferenceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ObjectType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PagingType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyAvailableValuesListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyModificationTypeType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.PropertyReferenceListType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.QueryType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceObjectShadowType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ResourceType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SchemaHandlingType.AccountType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.ScriptsType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SystemConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.SystemObjectsType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.TaskStatusType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserTemplateType;
import com.evolveum.midpoint.xml.ns._public.common.common_1.UserType;
import com.evolveum.midpoint.xml.schema.SchemaConstants;

/**
 * 
 * @author lazyman
 * 
 */
@Component
@Scope
public class ModelController {

	private static final Trace LOGGER = TraceManager.getTrace(ModelController.class);
	@Autowired(required = true)
	private transient ProvisioningService provisioning;
	@Autowired(required = true)
	private transient RepositoryService repository;

	public String addObject(ObjectType object, OperationResult result) throws ObjectAlreadyExistsException {
		Validate.notNull(object, "Object must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		Validate.notEmpty(object.getName(), "Object name must not be null or empty.");
		LOGGER.debug("Adding object {} with oid {} and name {}.", new Object[] {
				object.getClass().getSimpleName(), object.getOid(), object.getName() });

		OperationResult subResult = new OperationResult("Add Object");
		result.addSubresult(subResult);
		String oid = null;
		try {
			if (ProvisioningTypes.isManagedByProvisioning(object)) {
				oid = addProvisioningObject(object, subResult);
			} else {
				oid = addRepositoryObject(object, subResult);
			}
			subResult.recordSuccess();
		} catch (ObjectAlreadyExistsException ex) {

			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't add object", ex, object.getName());
			subResult.recordFatalError("Couldn't add object '" + object.getName() + "'.", ex);
		}

		LOGGER.debug(subResult.debugDump());
		return oid;
	}

	public String addUser(UserType user, UserTemplateType userTemplate, OperationResult result)
			throws ObjectAlreadyExistsException {
		Validate.notNull(user, "User must not be null.");
		Validate.notNull(userTemplate, "User template must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Adding user {}, oid {} using template {}, oid {}.",
				new Object[] { user.getName(), user.getOid(), userTemplate.getName(), userTemplate.getOid() });

		OperationResult subResult = new OperationResult("Add User With User Template");
		result.addSubresult(subResult);

		String oid = null;
		try {
			processUserTemplateForUser(user, userTemplate, subResult);
			oid = repository.addObject(user, subResult);
			subResult.recordSuccess();
		} catch (ObjectAlreadyExistsException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't add user {}, oid {} using template {}, oid {}", ex,
					user.getName(), user.getOid(), userTemplate.getName(), userTemplate.getOid());
			subResult.recordFatalError("Couldn't add user " + user.getName() + ", oid '" + user.getOid()
					+ "' using template " + userTemplate.getName() + ", oid '" + userTemplate.getOid() + "'",
					ex);
		}

		LOGGER.debug(subResult.debugDump());

		return oid;
	}

	public ObjectType getObject(String oid, PropertyReferenceListType resolve, OperationResult result)
			throws ObjectNotFoundException {
		Validate.notEmpty(oid, "Oid must not be null or empty.");
		Validate.notNull(resolve, "Property reference list must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Getting object with oid {}.", new Object[] { oid });

		OperationResult subResult = new OperationResult("Get Object");
		result.addSubresult(subResult);
		ObjectType object = null;
		try {
			object = getObjectFromRepository(oid, resolve, subResult, ObjectType.class);
			if (ProvisioningTypes.isManagedByProvisioning(object)) {
				object = getObjectFromProvisioning(oid, resolve, subResult, ObjectType.class);
			}
			subResult.recordSuccess();
		} catch (ObjectNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't get object {}", ex, oid);
			subResult.recordFatalError("Couldn't get object with oid '" + oid + "'.", ex);
		}

		resolveObjectAttributes(object, resolve, subResult);

		LOGGER.debug(subResult.debugDump());
		return object;
	}

	public ObjectListType listObjects(Class<? extends ObjectType> objectType, PagingType paging,
			OperationResult result) {
		Validate.notNull(objectType, "Object type must not be null.");
		Validate.notNull(paging, "Paging must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		ModelUtils.validatePaging(paging);
		LOGGER.debug("Listing objects of type {} from {} to {} ordered {} by {}.", new Object[] { objectType,
				paging.getOffset(), paging.getMaxSize(), paging.getOrderDirection(), paging.getOrderBy() });

		OperationResult subResult = new OperationResult("List Objects");
		result.addSubresult(subResult);
		ObjectListType list = null;
		try {
			if (ProvisioningTypes.isObjectTypeManagedByProvisioning(objectType)) {
				list = provisioning.listObjects(objectType, paging, subResult);
			} else {
				list = repository.listObjects(objectType, paging, subResult);
			}
			subResult.recordSuccess();
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't list objects", ex);
			subResult.recordFatalError("Couldn't list objects.", ex);
		}

		if (list == null) {
			list = new ObjectListType();
			list.setCount(0);
		}

		LOGGER.debug(subResult.debugDump());
		return list;
	}

	public ObjectListType searchObjects(QueryType query, PagingType paging, OperationResult result) {
		Validate.notNull(query, "Query must not be null.");
		Validate.notNull(paging, "Paging must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		ModelUtils.validatePaging(paging);
		LOGGER.debug("Searching objects from {} to {} ordered {} by {} (query in TRACE).", new Object[] {
				paging.getOffset(), paging.getMaxSize(), paging.getOrderDirection(), paging.getOrderBy() });
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace(DebugUtil.prettyPrint(query));
		}

		OperationResult subResult = new OperationResult("Search Objects");
		result.addSubresult(subResult);
		ObjectListType list = null;
		try {
			list = repository.searchObjects(query, paging, subResult);
			subResult.recordSuccess();
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't search objects in repository", ex);
			subResult.recordFatalError("Couldn't search objects in repository.", ex);
		}

		if (list == null) {
			list = new ObjectListType();
			list.setCount(0);
		}

		LOGGER.debug(subResult.debugDump());
		return list;
	}

	public void modifyObject(ObjectModificationType change, OperationResult result)
			throws ObjectNotFoundException {
		modifyObjectWithExclusion(change, null, result);
	}

	public void modifyObjectWithExclusion(ObjectModificationType change, String accountOid,
			OperationResult result) throws ObjectNotFoundException {
		Validate.notNull(change, "Object modification must not be null.");
		Validate.notEmpty(change.getOid(), "Change oid must not be null or empty.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Modifying object with oid {} with exclusion account oid {} (change in TRACE).",
				new Object[] { change.getOid(), accountOid });
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace(DebugUtil.prettyPrint(change));
		}

		if (change.getPropertyModification().isEmpty()) {
			return;
		}

		OperationResult subResult = new OperationResult("Modify Object With Exclusion");
		result.addSubresult(subResult);

		try {
			ObjectType object = getObjectFromRepository(change.getOid(), new PropertyReferenceListType(),
					subResult, ObjectType.class);
			if (ProvisioningTypes.isManagedByProvisioning(object)) {
				modifyProvisioningObjectWithExclusion(change, accountOid, subResult, object);
			} else {
				modifyRepositoryObjectWithExclusion(change, accountOid, subResult, object);
			}
			subResult.recordSuccess();
		} catch (ObjectNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't update object with oid {}", ex, change.getOid());
			subResult.recordFatalError("Couldn't update object with oid '" + change.getOid() + "'.", ex);
		}

		LOGGER.debug(subResult.debugDump());
	}

	public boolean deleteObject(String oid, OperationResult result) throws ObjectNotFoundException {
		Validate.notEmpty(oid, "Oid must not be null or empty.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Deleting object with oid {}.", new Object[] { oid });

		OperationResult subResult = new OperationResult("Delete Object");
		result.addSubresult(subResult);

		boolean deleted = false;
		try {
			ObjectType object = getObjectFromRepository(oid, new PropertyReferenceListType(), subResult,
					ObjectType.class);

			if (ProvisioningTypes.isManagedByProvisioning(object)) {
				ScriptsType scripts = getScripts(object, subResult);
				provisioning.deleteObject(oid, scripts, subResult);
			} else {
				if (object instanceof UserType) {
					deleteUserAccounts((UserType) object, subResult);
				}

				repository.deleteObject(oid, subResult);
			}
			deleted = true;
			subResult.recordSuccess();
		} catch (ObjectNotFoundException ex) {
			LoggingUtils.logException(LOGGER, "Couldn't delete object with oid {}", ex, oid);
			subResult.recordFatalError("Couldn't find object with oid '" + oid + "'.", ex);
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't delete object with oid {}", ex, oid);
			subResult.recordFatalError("Couldn't delete object with oid '" + oid + "'.", ex);
		}

		LOGGER.debug(subResult.debugDump());
		return deleted;
	}

	public PropertyAvailableValuesListType getPropertyAvailableValues(String oid,
			PropertyReferenceListType properties, OperationResult result) {
		Validate.notEmpty(oid, "Oid must not be null or empty.");
		Validate.notNull(properties, "Property reference list must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Getting property available values for object with oid {} (properties in TRACE).",
				new Object[] { oid });
		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace(DebugUtil.prettyPrint(properties));
		}

		throw new UnsupportedOperationException("Not implemented yet.");
	}

	public UserType listAccountShadowOwner(String accountOid, OperationResult result)
			throws ObjectNotFoundException {
		Validate.notEmpty(accountOid, "Account oid must not be null or empty.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Listing account shadow owner for account with oid {}.", new Object[] { accountOid });

		OperationResult subResult = new OperationResult("List Account Shadow Owner");
		result.addSubresult(subResult);

		UserType user = null;
		try {
			user = repository.listAccountShadowOwner(accountOid, subResult);
			subResult.recordSuccess();
		} catch (ObjectNotFoundException ex) {
			LoggingUtils.logException(LOGGER, "Account with oid {} doesn't exists", ex, accountOid);
			subResult.recordFatalError("Account with oid '" + accountOid + "' doesn't exists", ex);

			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't list account shadow owner from repository"
					+ " for account with oid {}", ex, accountOid);
			subResult.recordFatalError("Couldn't list account shadow owner for account with oid '"
					+ accountOid + "'.", ex);

		}

		LOGGER.debug(subResult.debugDump());
		return user;
	}

	public List<ResourceObjectShadowType> listResourceObjectShadows(String resourceOid,
			Class<? extends ObjectType> resourceObjectShadowType, OperationResult result)
			throws ObjectNotFoundException {
		Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Listing resource object shadows \"{}\" for resource with oid {}.", new Object[] {
				resourceObjectShadowType, resourceOid });

		OperationResult subResult = new OperationResult("List Resource Object Shadows");
		result.addSubresult(subResult);

		List<ResourceObjectShadowType> list = null;
		try {
			list = repository.listResourceObjectShadows(resourceOid, resourceObjectShadowType, subResult);
			subResult.recordSuccess();
		} catch (ObjectNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't list resource object shadows type "
					+ "{} from repository for resource, oid {}", ex, resourceObjectShadowType, resourceOid);
			subResult.recordFatalError(
					"Couldn't list resource object shadows type '" + resourceObjectShadowType
							+ "' from repository for resource, oid '" + resourceOid + "'.", ex);
		}

		if (list == null) {
			list = new ArrayList<ResourceObjectShadowType>();
		}

		LOGGER.debug(subResult.debugDump());
		return list;
	}

	public ObjectListType listResourceObjects(String resourceOid, QName objectType, PagingType paging,
			OperationResult result) {
		Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
		Validate.notNull(objectType, "Object type must not be null.");
		Validate.notNull(paging, "Paging must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		ModelUtils.validatePaging(paging);
		LOGGER.debug(
				"Listing resource objects {} from resource, oid {}, from {} to {} ordered {} by {}.",
				new Object[] { objectType, resourceOid, paging.getOffset(), paging.getMaxSize(),
						paging.getOrderDirection(), paging.getOrderDirection() });

		OperationResult subResult = new OperationResult("List Resource Objects");
		result.addSubresult(subResult);

		ObjectListType list = null;
		try {
			list = provisioning.listResourceObjects(resourceOid, objectType, paging, subResult);
			subResult.recordSuccess();
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't list resource objects of type {} for resource "
					+ "with oid {}", ex, objectType, resourceOid);
			subResult.recordFatalError("Couldn't list resource objects of type '" + objectType
					+ "' for resource, oid '" + resourceOid + "'.", ex);
		}

		if (list == null) {
			list = new ObjectListType();
			list.setCount(0);
		}

		LOGGER.debug(subResult.debugDump());
		return list;
	}

	public void testResource(String resourceOid, OperationResult result) {
		Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Testing resource with oid {}.", new Object[] { resourceOid });

		OperationResult subResult = null;
		try {
			subResult = provisioning.testResource(resourceOid);
			result.addSubresult(subResult);
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't test status for resource {}", ex, resourceOid);

			subResult = new OperationResult("Test Resource");
			subResult.recordFatalError("Couldn't test status for resource with oid '" + resourceOid + "'.",
					ex);
			result.addSubresult(subResult);
		}

		if (subResult != null) {
			LOGGER.debug(subResult.debugDump());
		} else {
			LOGGER.debug("Operation sub result was null (Error occured).");
		}
	}

	public void launchImportFromResource(String resourceOid, QName objectClass, OperationResult result)
			throws ObjectNotFoundException {
		Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
		Validate.notNull(objectClass, "Object class must not be null.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Launching import from resource with oid {} for object class {}.", new Object[] {
				resourceOid, objectClass });

		OperationResult subResult = new OperationResult("Launch Import From Resource");
		result.addSubresult(subResult);

		// TODO: WORK THIS OUT!!!!!!!!!!!!!!!
		// try {
		// provisioning.launchImportFromResource(resourceOid, objectClass,
		// subResult);
		// subResult.recordSuccess();
		// } catch (ObjectNotFoundException ex) {
		// throw ex;
		// } catch (Exception ex) {
		// LoggingUtils.logException(LOGGER,
		// "Couldn't launch import for objects of type {} on resource, "
		// + "oid {}", ex, objectClass, resourceOid);
		// subResult.recordFatalError("Couldn't launch import for objects of type '"
		// + objectClass
		// + "' on resource, oid '" + resourceOid + "'.", ex);
		// }

		LOGGER.debug(subResult.debugDump());
	}

	public TaskStatusType getImportStatus(String resourceOid, OperationResult result)
			throws ObjectNotFoundException {
		Validate.notEmpty(resourceOid, "Resource oid must not be null or empty.");
		Validate.notNull(result, "Result type must not be null.");
		LOGGER.debug("Getting import status for resource with oid {}.", new Object[] { resourceOid });

		OperationResult subResult = new OperationResult("Get Import Status");
		result.addSubresult(subResult);

		// TODO: WORK THIS OUT!!!!!!!!!!!!!!!
		// TaskStatusType status = null;
		// try {
		// status = provisioning.getImportStatus(resourceOid, subResult);
		// subResult.recordSuccess();
		// } catch (ObjectNotFoundException ex) {
		// throw ex;
		// } catch (Exception ex) {
		// LoggingUtils.logException(LOGGER,
		// "Couldn't get import status for resource {}", ex, resourceOid);
		// subResult.recordFatalError("Couldn't get import status for resource '"
		// + resourceOid + "'.", ex);
		// }

		LOGGER.debug(subResult.debugDump());
		return null;
	}

	private <T> T getObjectFromRepository(String oid, PropertyReferenceListType resolve,
			OperationResult result, Class<T> clazz) throws ObjectNotFoundException {
		return getObject(oid, resolve, result, clazz, false);
	}

	private <T> T getObjectFromProvisioning(String oid, PropertyReferenceListType resolve,
			OperationResult result, Class<T> clazz) throws ObjectNotFoundException {
		return getObject(oid, resolve, result, clazz, true);
	}

	@SuppressWarnings("unchecked")
	private <T> T getObject(String oid, PropertyReferenceListType resolve, OperationResult result,
			Class<T> clazz, boolean fromProvisioning) throws ObjectNotFoundException {
		T object = null;

		try {
			ObjectType objectType = null;
			if (fromProvisioning) {
				objectType = provisioning.getObject(oid, resolve, result);
			} else {
				objectType = repository.getObject(oid, resolve, result);
			}
			if (!clazz.isInstance(objectType)) {
				throw new ObjectNotFoundException("Bad object type returned for referenced oid '" + oid
						+ "'. Expected '" + clazz + "', but was '" + objectType.getClass() + "'.");
			} else {
				object = (T) objectType;
			}
		} catch (ObjectNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't get object with oid {}, expected type was {}.", ex,
					oid, clazz);
			throw new SystemException("Couldn't get object with oid '" + oid + "'.", ex);
		}

		return object;
	}

	private String addProvisioningObject(ObjectType object, OperationResult result)
			throws ObjectNotFoundException, ObjectAlreadyExistsException {
		if (object instanceof AccountShadowType) {
			AccountShadowType account = (AccountShadowType) object;
			processAccountCredentials(account, result);
		}

		try {
			ScriptsType scripts = getScripts(object, result);
			return provisioning.addObject(object, scripts, result);
		} catch (ObjectNotFoundException ex) {
			throw ex;
		} catch (ObjectAlreadyExistsException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new SystemException(ex.getMessage(), ex);
		}
	}

	private String addRepositoryObject(ObjectType object, OperationResult result)
			throws ObjectAlreadyExistsException, ObjectNotFoundException {
		if (object instanceof UserType) {
			// At first we get default user template from system configuration
			SystemConfigurationType systemConfiguration = getObject(
					SystemObjectsType.SYSTEM_CONFIGURATION.value(),
					ModelUtils.createPropertyReferenceListType("defaultUserTemplate"), result,
					SystemConfigurationType.class, false);

			UserTemplateType userTemplate = systemConfiguration.getDefaultUserTemplate();
			processUserTemplateForUser((UserType) object, userTemplate, result);
		}

		try {
			return repository.addObject(object, result);
		} catch (ObjectAlreadyExistsException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't add object {} to repository", ex, object.getName());
			throw new SystemException(ex.getMessage(), ex);
		}
	}

	private void resolveObjectAttributes(ObjectType object, PropertyReferenceListType resolve,
			OperationResult result) {
		if (object == null) {
			return;
		}

		if (object instanceof UserType) {
			resolveUserAttributes((UserType) object, resolve, result);
		} else if (object instanceof AccountShadowType) {
			resolveAccountAttributes((AccountShadowType) object, resolve, result);
		}
	}

	private void resolveUserAttributes(UserType user, PropertyReferenceListType resolve,
			OperationResult result) {
		if (!Utils.haveToResolve("Account", resolve)) {
			return;
		}

		List<ObjectReferenceType> refToBeDeleted = new ArrayList<ObjectReferenceType>();
		for (ObjectReferenceType accountRef : user.getAccountRef()) {
			try {
				AccountShadowType account = getObjectFromProvisioning(accountRef.getOid(), resolve, result,
						AccountShadowType.class);
				user.getAccount().add(account);
				refToBeDeleted.add(accountRef);

				resolveAccountAttributes(account, resolve, result);
			} catch (Exception ex) {
				LoggingUtils.logException(LOGGER, "Couldn't resolve account with oid {}", ex,
						accountRef.getOid());
				throw new SystemException(ex.getMessage(), ex);
			}
		}
		user.getAccountRef().removeAll(refToBeDeleted);
	}

	private void resolveAccountAttributes(AccountShadowType account, PropertyReferenceListType resolve,
			OperationResult result) {
		if (!Utils.haveToResolve("Resource", resolve)) {
			return;
		}

		ObjectReferenceType reference = account.getResourceRef();
		if (reference == null || StringUtils.isEmpty(reference.getOid())) {
			LOGGER.debug("Skipping resolving resource for account {}, resource reference is null or "
					+ "doesn't contain oid.", new Object[] { account.getName() });
		}

		try {
			ResourceType resource = getObjectFromProvisioning(account.getResourceRef().getOid(), resolve,
					result, ResourceType.class);
			account.setResource(resource);
			account.setResourceRef(null);
		} catch (Exception ex) {
			LoggingUtils
					.logException(LOGGER, "Couldn't resolve resource with oid {}", ex, reference.getOid());
			throw new SystemException(ex.getMessage(), ex);
		}
	}

	private ScriptsType getScripts(ObjectType object, OperationResult result) throws ObjectNotFoundException {
		ScriptsType scripts = null;
		if (object instanceof ResourceType) {
			ResourceType resource = (ResourceType) object;
			scripts = resource.getScripts();
		} else if (object instanceof ResourceObjectShadowType) {
			ResourceObjectShadowType resourceObject = (ResourceObjectShadowType) object;
			if (resourceObject.getResource() != null) {
				scripts = resourceObject.getResource().getScripts();
			} else {
				ObjectReferenceType reference = resourceObject.getResourceRef();
				ResourceType resObject = getObjectFromProvisioning(reference.getOid(),
						new PropertyReferenceListType(), result, ResourceType.class);
				scripts = resObject.getScripts();
			}
		}

		if (scripts == null) {
			scripts = new ScriptsType();
		}

		return scripts;
	}

	private void deleteUserAccounts(UserType user, OperationResult result) throws ObjectNotFoundException {
		List<AccountShadowType> accountsToBeDeleted = new ArrayList<AccountShadowType>();
		for (AccountShadowType account : user.getAccount()) {
			if (deleteObject(account.getOid(), result)) {
				accountsToBeDeleted.add(account);
			}
		}
		user.getAccount().removeAll(accountsToBeDeleted);

		List<ObjectReferenceType> refsToBeDeleted = new ArrayList<ObjectReferenceType>();
		for (ObjectReferenceType accountRef : user.getAccountRef()) {
			if (deleteObject(accountRef.getOid(), result)) {
				refsToBeDeleted.add(accountRef);
			}
		}
		user.getAccountRef().removeAll(refsToBeDeleted);

		// TODO: save updated user, create property changes
		ObjectModificationType change = createUserModification(accountsToBeDeleted, refsToBeDeleted);
		change.setOid(user.getOid());
		try {
			repository.modifyObject(change, result);
		} catch (ObjectNotFoundException ex) {
			throw ex;
		} catch (Exception ex) {
			LoggingUtils.logException(LOGGER, "Couldn't update user {} after accounts was deleted", ex,
					user.getName());
			throw new SystemException(ex.getMessage(), ex);
		}
	}

	private ObjectModificationType createUserModification(List<AccountShadowType> accountsToBeDeleted,
			List<ObjectReferenceType> refsToBeDeleted) {
		ObjectModificationType change = new ObjectModificationType();
		for (ObjectReferenceType reference : refsToBeDeleted) {
			PropertyModificationType propertyChangeType = ObjectTypeUtil.createPropertyModificationType(
					PropertyModificationTypeType.delete, null, new QName(SchemaConstants.NS_C, "accountRef"),
					reference);

			change.getPropertyModification().add(propertyChangeType);
		}

		for (AccountShadowType account : accountsToBeDeleted) {
			PropertyModificationType propertyChangeType = ObjectTypeUtil.createPropertyModificationType(
					PropertyModificationTypeType.delete, null, new QName(SchemaConstants.NS_C, "account"),
					account.getOid());

			change.getPropertyModification().add(propertyChangeType);
		}

		return change;
	}

	private void processAccountCredentials(AccountShadowType account, OperationResult result)
			throws ObjectNotFoundException {
		// inserting credentials to account if needed (with generated password)
		ResourceType resource = account.getResource();
		if (resource == null) {
			resource = getObjectFromProvisioning(account.getResourceRef().getOid(),
					new PropertyReferenceListType(), result, ResourceType.class);
		}

		if (resource == null || resource.getSchemaHandling() == null) {
			return;
		}

		AccountType accountType = ModelUtils.getAccountTypeDefinitionFromSchemaHandling(account, resource);
		if (accountType == null || accountType.getCredentials() == null) {
			return;
		}

		AccountType.Credentials credentials = accountType.getCredentials();
		int randomPasswordLength = -1;
		if (credentials.getRandomPasswordLength() != null) {
			randomPasswordLength = credentials.getRandomPasswordLength().intValue();
		}

		if (randomPasswordLength != -1 && ModelUtils.getPassword(account).getAny() == null) {
			ModelUtils.generatePassword(account, randomPasswordLength);
		}
	}

	@SuppressWarnings("unchecked")
	private void modifyProvisioningObjectWithExclusion(ObjectModificationType change, String accountOid,
			OperationResult result, ObjectType object) throws ObjectNotFoundException, SchemaException,
			CommunicationException {
		if (object instanceof ResourceObjectShadowType) {
			object = getObject(object.getOid(), new PropertyReferenceListType(), result,
					ResourceObjectShadowType.class, true);

			UserType user = null;
			try {
				user = listAccountShadowOwner(object.getOid(), result);
			} catch (ObjectNotFoundException ex) {
				// we didn't find shadow owner, in next step we skip outbound
				// schema handling processing.
			} catch (Exception ex) {
				LoggingUtils.logException(LOGGER, "Couldn't list account shadow owner for for {}", ex,
						object.getName());
			}

			try {
				PatchXml patchXml = new PatchXml();
				String xmlPatchedObject = patchXml.applyDifferences(change, object);
				object = ((JAXBElement<ResourceObjectShadowType>) JAXBUtil.unmarshal(xmlPatchedObject))
						.getValue();

				ObjectModificationType newChange = processOutboundSchemaHandling(user,
						(ResourceObjectShadowType) object, result);
				if (newChange != null) {
					change = newChange;
				}
			} catch (Exception ex) {
				// TODO: error handling
				ex.printStackTrace();
			}
		}

		ScriptsType scripts = getScripts(object, result);
		if (StringUtils.isNotEmpty(change.getOid())) {
			provisioning.modifyObject(change, scripts, result);
		}
	}

	@SuppressWarnings("unchecked")
	private void modifyRepositoryObjectWithExclusion(ObjectModificationType change, String accountOid,
			OperationResult result, ObjectType object) throws ObjectNotFoundException, SchemaException {
		if (object instanceof UserType) {
			UserType user = (UserType) object;
			// processing add account
			for (PropertyModificationType propertyChange : change.getPropertyModification()) {
				if (!PropertyModificationTypeType.add.equals(propertyChange.getModificationType())
						|| propertyChange.getValue() == null || propertyChange.getValue().getAny().isEmpty()) {
					continue;
				}

				Node node = propertyChange.getValue().getAny().get(0);
				if ("account".equals(node.getLocalName())) {
					try {
						AccountShadowType account = ((JAXBElement<AccountShadowType>) JAXBUtil
								.unmarshal(DOMUtil.serializeDOMToString(node))).getValue();

						ObjectModificationType accountChange = processOutboundSchemaHandling(user, account,
								result);
						PatchXml patchXml = new PatchXml();
						String accountXml = patchXml.applyDifferences(accountChange, account);
						account = ((JAXBElement<AccountShadowType>) JAXBUtil.unmarshal(accountXml))
								.getValue();

						String newAccountOid = addObject(account, result);
						ObjectReferenceType accountRef = ModelUtils.createReference(newAccountOid,
								ObjectTypes.ACCOUNT);
						Element accountRefElement = JAXBUtil.jaxbToDom(accountRef,
								SchemaConstants.I_ACCOUNT_REF, DOMUtil.getDocument());

						propertyChange.getValue().getAny().clear();
						propertyChange.getValue().getAny().add(accountRefElement);
					} catch (Exception ex) {
						// TODO: error handling
						ex.printStackTrace();
					}
				}
			}

			// if we're updating user, process outbound for every account
			List<ObjectReferenceType> accountRefs = user.getAccountRef();
			for (ObjectReferenceType accountRef : accountRefs) {
				if (StringUtils.isNotEmpty(accountOid) && accountOid.equals(accountRef.getOid())) {
					// preventing cycles while updating resource object shadows
					continue;
				}

				try {
					AccountShadowType account = getObject(accountRef.getOid(),
							ModelUtils.createPropertyReferenceListType("Resource"), result,
							AccountShadowType.class, true);
					SchemaHandler schemaHandler = new SchemaHandlerImpl();
					ObjectModificationType accountChange = schemaHandler.processOutboundHandling(user,
							account, result);
					modifyObjectWithExclusion(accountChange, accountOid, result);
					ScriptsType scripts = getScripts(account, result);

					provisioning.modifyObject(accountChange, scripts, result);
				} catch (Exception ex) {

					ex.printStackTrace();
				}
			}
		}

		repository.modifyObject(change, result);
	}

	private ObjectModificationType processOutboundSchemaHandling(UserType user,
			ResourceObjectShadowType object, OperationResult result) {
		ObjectModificationType change = null;
		if (user != null) {
			try {
				SchemaHandler schemaHandler = new SchemaHandlerImpl();
				change = schemaHandler.processOutboundHandling(user, (ResourceObjectShadowType) object,
						result);
			} catch (Exception ex) {
				LoggingUtils.logException(LOGGER, "Couldn't process outbound schema handling for {}", ex,
						object.getName());
			}
		} else {
			LOGGER.debug("Skipping outbound schema handling processing for {} (no user defined).",
					new Object[] { object.getName() });
		}

		return change;
	}

	@SuppressWarnings("unchecked")
	private void processUserTemplateForUser(UserType user, UserTemplateType userTemplate,
			OperationResult result) {
		if (userTemplate == null) {
			return;
		}

		List<AccountConstructionType> accountConstructions = userTemplate.getAccountConstruction();
		for (AccountConstructionType construction : accountConstructions) {
			OperationResult addObject = new OperationResult("Link Object To User");
			try {
				ObjectReferenceType resourceRef = construction.getResourceRef();
				ResourceType resource = getObject(resourceRef.getOid(), new PropertyReferenceListType(),
						result, ResourceType.class, true);

				AccountType accountType = ModelUtils.getAccountTypeFromHandling(construction.getType(),
						resource);

				AccountShadowType account = new AccountShadowType();
				account.setObjectClass(accountType.getObjectClass());
				account.setName(resource.getName() + "-" + user.getName());
				account.setResourceRef(resourceRef);

				ObjectModificationType changes = processOutboundSchemaHandling(user, account, result);

				PatchXml patchXml = new PatchXml();
				String accountXml = patchXml.applyDifferences(changes, account);
				account = ((JAXBElement<AccountShadowType>) JAXBUtil.unmarshal(accountXml)).getValue();

				String accountOid = addObject(account, result);
				user.getAccountRef().add(ModelUtils.createReference(accountOid, ObjectTypes.ACCOUNT));
				// XXX: groups, roles later
				addObject.recordSuccess();
			} catch (Exception ex) {
				// TODO: logging
				ex.printStackTrace();
				addObject.recordFatalError("Something went terribly wrong.", ex);
			}
		}
	}
}
