/*
 * Copyright 2015 Nastel Technologies, Inc.
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
package org.tnt4j.pingjmx.factory;

import javax.management.MBeanServer;

import org.tnt4j.pingjmx.core.Pinger;

/**
 * <p> 
 * This interface defines a way to obtain underlying implementation 
 * of the pinger object that actually samples underlying MBeans.
 * </p>
 * 
 * @version $Revision: 1 $
 * 
 * @see Pinger
 */
public interface PingFactory {
	
	/**
	 * Create a default instance with default MBean server instance
	 * <code>ManagementFactory.getPlatformMBeanServer()</code>
	 * 
	 * @see Pinger
	 */
	Pinger newInstance();

	/**
	 * Create a default instance with a given MBean server instance
	 * 
	 * @param mserver MBean server instance
	 * @see Pinger
	 */
	Pinger newInstance(MBeanServer mserver);
}
