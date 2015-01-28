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
package org.tnt4j.pingjmx;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;

import com.nastel.jkool.tnt4j.core.Activity;
import com.nastel.jkool.tnt4j.core.ActivityListener;
import com.nastel.jkool.tnt4j.core.PropertySnapshot;

/**
 * <p> 
 * This class provides implementation for handling ping/heart-beats
 * generated by <code>PingJmx</code> class.
 * </p>
 * 
 * @see PingJmx
 * @version $Revision: 1 $
 */
public class PingJmxListener implements ActivityListener {
	String mbeanFilter;
	long sampleCount = 0;
	MBeanServer mbeanServer;
	HashMap<ObjectName, MBeanInfo> mbeans = new HashMap<ObjectName, MBeanInfo>(89);
	HashMap<MBeanAttributeInfo, MBeanAttributeInfo> excAttrs = new HashMap<MBeanAttributeInfo, MBeanAttributeInfo>(89);

	/**
	 * Create new instance of <code>PingJmxListener</code> with a given
	 * MBean server and a set of filters.
	 *
	 * @param mserver MBean server instance
	 * @param filter JMX filters semicolon separated
	 *  
	 */
	public PingJmxListener(MBeanServer mserver, String filter) {
		mbeanServer = mserver;
		mbeanFilter = filter;
	}

	/**
	 * Obtain associated MBean server instance.
	 * 
	 * @return MBean server instance associated with this listener 
	 */
	public MBeanServer getMBeanServer() {
		return mbeanServer;
	}

	/**
	 * Load JMX beans based on a configured JMX filter list.
	 * All loaded MBeans are stored in <code>HashMap</code>.
	 */
	private void loadJmxBeans() {
		try {
			StringTokenizer tk = new StringTokenizer(mbeanFilter, ";");
			Vector<ObjectName> nFilters = new Vector<ObjectName>(5);
			while (tk.hasMoreTokens()) {
				nFilters.add(new ObjectName(tk.nextToken()));
			}
			for (ObjectName nameFilter : nFilters) {
				Set<?> set = mbeanServer.queryNames(nameFilter, nameFilter);
				for (Iterator<?> it = set.iterator(); it.hasNext();) {
					ObjectName oname = (ObjectName) it.next();
					mbeans.put(oname, mbeanServer.getMBeanInfo(oname));
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * Sample jmx beans based on a configured jmx filter list
	 * and store within given activity as snapshots.
	 * 
	 * @param activity instance where sampled mbeans attributes are stored
	 * @return number of metrics loaded from all mbeans
	 */
	private int sampleMbeans(Activity activity) {
		int pCount = 0;
		for (Entry<ObjectName, MBeanInfo> entry: mbeans.entrySet()) {
			ObjectName name = entry.getKey();
			MBeanInfo info = entry.getValue();
			MBeanAttributeInfo[] attr = info.getAttributes();
			
			PropertySnapshot snapshot = new PropertySnapshot(name.getDomain(), name.getCanonicalName());
			for (int i = 0; i < attr.length; i++) {
				MBeanAttributeInfo jinfo = attr[i];
				if (jinfo.isReadable() && !attrExcluded(jinfo)) {
					try {
						Object value = mbeanServer.getAttribute(name, jinfo.getName());
						processJmxValue(snapshot, jinfo, jinfo.getName(), value);
					} catch (Throwable ex) {
						exclude(jinfo);
						System.err.println("Skipping attribute=" + jinfo + ", reason=" + ex);
					}
				}
			}
			if (snapshot.size() > 0) {
				activity.addSnapshot(snapshot);
				pCount += snapshot.size();
			}
		}
		return pCount;
	}

	/**
	 * Determine if a given attribute to be excluded from sampling.	
	 * 
	 * @param jinfo attribute info
	 * @return true when attribute should be excluded, false otherwise
	 */
	private boolean attrExcluded(MBeanAttributeInfo jinfo) {
	    return excAttrs.get(jinfo) != null;
    }

	/**
	 * Mark a given attribute to be excluded from sampling.	
	 * 
	 * @param jinfo attribute info
	 */
	private void exclude(MBeanAttributeInfo jinfo) {
	    excAttrs.put(jinfo, jinfo);
    }

	/**
	 * Process/extract value from a given MBean attribute
	 * 
	 * @param snapshot instance where extracted attribute is stored
	 * @param jinfo attribute info
	 * @param property name to be assigned to given attribute value
	 * @param value associated with attribute
	 */
	private void processJmxValue(PropertySnapshot snapshot, MBeanAttributeInfo jinfo, String propName, Object value) {
		if (value != null && !value.getClass().isArray()) {
			if (value instanceof CompositeData) {
				CompositeData cdata = (CompositeData) value;
				Set<String> keys = cdata.getCompositeType().keySet();
				for (String key: keys) {
					Object cval = cdata.get(key);
					processJmxValue(snapshot, jinfo, propName + "\\" + key, cval);
				}
			} else {
				snapshot.add(propName, value);
			}
		}
	}
	
	/**
	 * Finish processing of the activity sampling
	 * 
	 * @param activity instance
	 */
	private int finish(Activity activity) {
		PropertySnapshot snapshot = new PropertySnapshot(activity.getName(), "SampleStats");
		snapshot.add("sample.count", sampleCount);
		activity.addSnapshot(snapshot);		
		return snapshot.size();
	}
	
	@Override
	public void started(Activity activity) {
		if (mbeans.size() == 0) {
			loadJmxBeans();
		}
	}

	@Override
	public void stopped(Activity activity) {
		sampleCount++;
		int metrics = sampleMbeans(activity);		
		metrics += finish(activity);		
		
		System.out.println(activity.getName()
				+ ": id=" + activity.getTrackingId() 
				+ ", mbean.server=" + mbeanServer
				+ ", mbean.count=" + mbeanServer.getMBeanCount()
				+ ", elasped.usec=" + activity.getElapsedTime() 
				+ ", snap.count=" + activity.getSnapshotCount() 
				+ ", id.count=" + activity.getIdCount()
				+ ", sampled.mbeans.count=" + mbeans.size()
				+ ", sampled.metric.count=" + metrics
				+ ", exclude.attrs=" + excAttrs.size()
				);
	}
}