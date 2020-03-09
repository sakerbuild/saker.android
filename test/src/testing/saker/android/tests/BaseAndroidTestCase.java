package testing.saker.android.tests;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;

import saker.build.thirdparty.saker.util.ObjectUtils;
import testing.saker.build.tests.EnvironmentTestCaseConfiguration;
import testing.saker.nest.util.NestRepositoryCachingEnvironmentTestCase;

public abstract class BaseAndroidTestCase extends NestRepositoryCachingEnvironmentTestCase {
	@Override
	protected Set<EnvironmentTestCaseConfiguration> getTestConfigurations() {
		LinkedHashSet<EnvironmentTestCaseConfiguration> result = new LinkedHashSet<>();
		for (EnvironmentTestCaseConfiguration tcc : super.getTestConfigurations()) {
			TreeMap<String, String> userparams = ObjectUtils.newTreeMap(tcc.getEnvironmentUserParameters());
			userparams.put("saker.android.sdk.install.location",
					testParameters.get("AndroidSDKLocationEnvironmentUserParameter"));
			result.add(EnvironmentTestCaseConfiguration.builder(tcc).setEnvironmentUserParameters(userparams).build());
		}
		return result;
	}

	@Override
	protected String getRepositoryStorageConfiguration() {
		return "[:params, :server]";
	}
}
