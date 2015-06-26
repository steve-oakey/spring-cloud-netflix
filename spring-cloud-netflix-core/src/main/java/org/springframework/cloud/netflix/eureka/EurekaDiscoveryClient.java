/*
 * Copyright 2013-2015 the original author or authors.
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

package org.springframework.cloud.netflix.eureka;

import static com.netflix.appinfo.InstanceInfo.PortType.SECURE;
import static com.netflix.appinfo.InstanceInfo.PortType.UNSECURE;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.discovery.DiscoveryServiceInstance;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;

/**
 * @author Spencer Gibb
 */
public class EurekaDiscoveryClient implements DiscoveryClient {

	public static final String DESCRIPTION = "Spring Cloud Eureka Discovery Client";

	@Autowired
	private EurekaInstanceConfigBean config;

	@Autowired
	private com.netflix.discovery.DiscoveryClient discovery;

	@Override
	public String description() {
		return DESCRIPTION;
	}

	@Override
	public DiscoveryServiceInstance getLocalServiceInstance() {
		return new DiscoveryServiceInstance() {
			@Override
			public String getServiceId() {
				return EurekaDiscoveryClient.this.config.getAppname();
			}

			@Override
			public String getHost() {
				return EurekaDiscoveryClient.this.config.getHostname();
			}

			@Override
			public int getPort() {
				return EurekaDiscoveryClient.this.config.getNonSecurePort();
			}

			@Override
			public boolean isSecure() {
				return EurekaDiscoveryClient.this.config.getSecurePortEnabled();
			}

			@Override
			public URI getUri() {
				return DefaultServiceInstance.getUri(this);
			}

			@Override
			public URI getHealthCheckUri() {
				String url = isSecure() ? EurekaDiscoveryClient.this.config
						.getSecureHealthCheckUrl() : EurekaDiscoveryClient.this.config
						.getHealthCheckUrl();
				if (StringUtils.hasText(url)) {
					return URI.create(url);
				}
				return UriComponentsBuilder.fromUri(getUri())
						.path(EurekaDiscoveryClient.this.config.getHealthCheckUrlPath())
						.build().toUri();
			}

			@Override
			public URI getStatusPageUri() {
				String url = EurekaDiscoveryClient.this.config.getStatusPageUrl();
				if (url != null) {
					return URI.create(url);
				}
				return UriComponentsBuilder.fromUri(getUri())
						.path(EurekaDiscoveryClient.this.config.getStatusPageUrlPath())
						.build().toUri();
			}
		};
	}

	@Override
	public List<DiscoveryServiceInstance> getInstances(String serviceId) {
		List<InstanceInfo> infos = this.discovery.getInstancesByVipAddress(serviceId,
				false);
		List<DiscoveryServiceInstance> instances = new ArrayList<>();
		for (InstanceInfo info : infos) {
			instances.add(new EurekaServiceInstance(info));
		}
		return instances;
	}

	static class EurekaServiceInstance implements DiscoveryServiceInstance {
		private InstanceInfo instance;

		EurekaServiceInstance(InstanceInfo instance) {
			this.instance = instance;
		}

		@Override
		public String getServiceId() {
			return this.instance.getAppName();
		}

		@Override
		public String getHost() {
			return this.instance.getHostName();
		}

		@Override
		public int getPort() {
			// assume if unsecure is enabled, that is the default
			if (this.instance.isPortEnabled(UNSECURE)
					|| !this.instance.isPortEnabled(SECURE)) {
				return this.instance.getPort();
			}
			return this.instance.getSecurePort();
		}

		@Override
		public boolean isSecure() {
			return this.instance.isPortEnabled(SECURE);
		}

		@Override
		public URI getUri() {
			return DefaultServiceInstance.getUri(this);
		}

		@Override
		public URI getHealthCheckUri() {
			Set<String> healthChecks = this.instance.getHealthCheckUrls();
			String url = null;
			for (String hc : healthChecks) {
				url = hc;
				if (isSecure() && hc.startsWith("https")) {
					break;
				}
			}
			return URI.create(url);
		}

		@Override
		public URI getStatusPageUri() {
			return URI.create(this.instance.getStatusPageUrl());
		}
	}

	@Override
	public List<String> getServices() {
		Applications applications = this.discovery.getApplications();
		if (applications == null) {
			return Collections.emptyList();
		}
		List<Application> registered = applications.getRegisteredApplications();
		List<String> names = new ArrayList<String>();
		for (Application app : registered) {
			if (app.getInstances().isEmpty()) {
				continue;
			}
			names.add(app.getName().toLowerCase());

		}
		return names;
	}

}
