/**
 * Copyright 2018 Mike Hummel
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
package de.mhus.osgi.services.deploy;

import java.io.File;

import de.mhus.lib.core.MLog;
import de.mhus.osgi.services.DeployService;

//@Component(provide=DeployService.class)
public abstract class AbstractDeployService extends MLog implements DeployService {

	private File dir;

	@Override
	public void setDeployDirectory(String path, File dir) {
		this.dir = dir;
	}

	@Override
	public File getDeployDirectory() {
		return dir;
	}

	@Override
	public String[] getResourcePathes() {
		return new String[] {getResourcePath()};
	}

	protected abstract String getResourcePath();

}
