package io.pivotal.cdm.service;

import static io.pivotal.cdm.config.LCCatalogConfig.COPY;
import static io.pivotal.cdm.model.BrokerActionState.*;
import io.pivotal.cdm.dto.InstancePair;
import io.pivotal.cdm.model.*;
import io.pivotal.cdm.provider.CopyProvider;
import io.pivotal.cdm.repo.BrokerActionRepository;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.cloudfoundry.community.servicebroker.exception.*;
import org.cloudfoundry.community.servicebroker.model.*;
import org.cloudfoundry.community.servicebroker.service.ServiceInstanceService;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.Service;

/**
 * Creating a service instance is a no op for the copy operations, we simply
 * assume that the prod instance exists.
 * 
 * @author jkruck
 *
 */
@Service
public class LCServiceInstanceService implements ServiceInstanceService {
	private Logger logger = Logger.getLogger(LCServiceInstanceService.class);

	LCServiceInstanceManager instanceManager;

	private CopyProvider provider;

	private String sourceInstanceId;

	private BrokerActionRepository brokerRepo;

	@Autowired
	public LCServiceInstanceService(
			final CopyProvider provider,
			@Value("#{environment.SOURCE_INSTANCE_ID}") final String sourceInstanceId,
			final BrokerActionRepository brokerRepo,
			final LCServiceInstanceManager instanceManager) {
		this.provider = provider;
		this.sourceInstanceId = sourceInstanceId;
		this.brokerRepo = brokerRepo;
		this.instanceManager = instanceManager;
	}

	@Override
	public ServiceInstance createServiceInstance(
			CreateServiceInstanceRequest request)
			throws ServiceInstanceExistsException, ServiceBrokerException {

		String id = request.getServiceInstanceId();
		log(id, "Creating service instance", IN_PROGRESS);
		throwIfDuplicate(id);

		try {
			ServiceInstance instance = new ServiceInstance(request);
			String copyId = COPY.equals(request.getPlanId()) ? provider
					.createCopy(sourceInstanceId) : sourceInstanceId;

			instanceManager.saveInstance(instance, copyId);

			log(id, "Created service instance", COMPLETE);
			return instance;
		} catch (Exception e) {
			log(id, "Failed to create service instance: " + e.getMessage(),
					FAILED);
			throw e;
		}
	}

	@Override
	public ServiceInstance deleteServiceInstance(
			DeleteServiceInstanceRequest request) throws ServiceBrokerException {
		String id = request.getServiceInstanceId();
		log(id, "Deleting service instance", IN_PROGRESS);
		ServiceInstance instance = instanceManager.getInstance(id);
		if (null == instance) {
			log(id, "Service instance not found", FAILED);
			return null;
		}

		try {
			if (COPY.equals(request.getPlanId())) {
				provider.deleteCopy(instanceManager.getCopyIdForInstance(id));
			}
			log(id, "Deleted service instance", COMPLETE);
			return instanceManager.removeInstance(id);
		} catch (ServiceBrokerException e) {
			log(id, "Failed to delete service instance: " + e.getMessage(),
					FAILED);
			throw e;
		}
	}

	@Override
	public ServiceInstance getServiceInstance(String id) {
		return instanceManager.getInstance(id);
	}

	@Override
	public ServiceInstance updateServiceInstance(
			UpdateServiceInstanceRequest request)
			throws ServiceInstanceUpdateNotSupportedException,
			ServiceBrokerException, ServiceInstanceDoesNotExistException {

		log(request.getServiceInstanceId(),
				"Updating service instance is not supported", FAILED);
		throw new ServiceInstanceUpdateNotSupportedException(
				"Cannot update plan " + request.getPlanId());
	}

	public String getInstanceIdForServiceInstance(String serviceInstanceId) {
		//@formatter:off
		return instanceManager.getInstances()
				.stream()
				.filter(s -> s.getRight().getServiceInstanceId().equals(serviceInstanceId))
				.findFirst().
				get().getLeft();
		//@formatter:on
	}

	public List<InstancePair> getProvisionedInstances() {
		//@formatter:off
		return instanceManager.getInstances()
				.stream()
				.map(i -> new InstancePair(sourceInstanceId, i.getLeft()))
				.collect(Collectors.toList());
		//@formatter:on
	}

	public String getSourceInstanceId() {
		return sourceInstanceId;
	}

	private void log(String id, String msg, BrokerActionState state) {
		String logMsg = msg + " " + id;

		if (FAILED == state) {
			logger.error(logMsg);
		} else {
			logger.info(logMsg);
		}
		brokerRepo.save(new BrokerAction(id, state, msg));
	}

	private void throwIfDuplicate(String id)
			throws ServiceInstanceExistsException {
		if (null != instanceManager.getInstance(id)) {
			log(id, "Duplicate service instance requested", FAILED);
			throw new ServiceInstanceExistsException(
					instanceManager.getInstance(id));
		}
	}
}