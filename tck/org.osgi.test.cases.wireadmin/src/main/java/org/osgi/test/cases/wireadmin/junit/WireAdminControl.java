/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
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
 *
 * SPDX-License-Identifier: Apache-2.0 
 *******************************************************************************/
package org.osgi.test.cases.wireadmin.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.osgi.framework.Bundle;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.permissionadmin.PermissionAdmin;
import org.osgi.service.permissionadmin.PermissionInfo;
import org.osgi.service.wireadmin.BasicEnvelope;
import org.osgi.service.wireadmin.Consumer;
import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;
import org.osgi.service.wireadmin.WireAdmin;
import org.osgi.service.wireadmin.WireAdminEvent;
import org.osgi.service.wireadmin.WireAdminListener;
import org.osgi.service.wireadmin.WireConstants;
import org.osgi.service.wireadmin.WirePermission;
import org.osgi.test.common.annotation.InjectBundleInstaller;
import org.osgi.test.common.install.BundleInstaller;

/**
 * Contains the test methods of the wireadmin test case
 *
 * $Log$ Revision 1.9 2006/05/22 10:17:14 sboshev Fixed bug - filter test did
 * fail sometimes because the Thread didn't wait for 500ms as requested.
 *
 * Revision 1.8 2005/11/30 08:44:34 sboshev Fixed a bug which caused sometimes
 * the test case to fail
 *
 * Revision 1.7 2005/08/03 16:32:17 pdobrev fix last issues
 *
 * Revision 1.6 2005/07/27 15:13:31 pdobrev fixes issue#387
 *
 * Revision 1.4 2004/12/03 09:12:32 pkriens Added service project Revision 1.3
 * 2004/11/03 11:47:09 pkriens Format and clean up of warnings Revision 1.2
 * 2004/11/03 10:55:32 pkriens Format and clean up of warnings Revision 1.1
 * 2004/07/07 13:15:26 pkriens *** empty log message ***
 *
 * Revision 1.7 2003/12/16 15:06:17 vpanushev 1. test_wireadmin_DeleteWire is
 * changed to reproduce the case described in issue #245 from the issue tracker
 * 2. test_wireadmin_RestartWireadmin rewriten
 *
 * Revision 1.6 2003/11/14 07:18:29 vpanushev resolved issues 243 and 246
 *
 *
 * @author Neviana Ducheva, Vasil Panushev
 */
public class WireAdminControl extends WireAdminTestSupport {

	@InjectBundleInstaller
	BundleInstaller		installer;

	String				testMethodName;

	@BeforeEach
	void testMethodName(TestInfo testInfo) {
		testMethodName = testInfo.getTestMethod().get().getName();
	}

	private PermissionAdmin	pa;
	private Helper			helper;
	private WireAdmin		wa;
	private Hashtable<String,Wire[]>	returnedWires;
	public int				synchCounterx;
	private List<String>				permBundles;

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// /////////////////////////////////////////wire admin test methods start
	// here ///////////////////////////////////////////
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/**
	 * Increase the test case timeout
	 */
	@BeforeEach
	public synchronized void setUp() {
		returnedWires = new Hashtable<>();
		clearSync();
		permBundles = new ArrayList<>();
		wa = getService(WireAdmin.class);
		pa = getService(PermissionAdmin.class);
		helper = new Helper(context, this);
		Helper.deleteAllWires(wa);
	}

	@AfterEach
	public synchronized void tearDown() {
		helper.unregisterAll();
		Helper.deleteAllWires(wa);
		removePermissions();
		ungetService(pa);
		ungetService(wa);
	}

	/**
	 * @param requiredSyncs
	 * @param maxDelay
	 * @throws InterruptedException
	 */
	static Object	lock	= new Object();

	private synchronized void waitForSync(int requiredSyncs, int maxDelay)
			throws InterruptedException {
		// System.out.println("**** delaying " + synchCounterx + " " +
		// requiredSyncs);
		while (synchCounterx < requiredSyncs && maxDelay-- > 0) {
			// Thread.sleep(100);
			wait(100 * TestCaseProperties.getScaling());
		}
		if (maxDelay <= 0)
			fail("**** timed out");
		synchCounterx = 0;
	}

	private synchronized void waitForNoSync(int maxDelay)
			throws InterruptedException {
		// System.out.println("**** delaying " + synchCounterx + " " +
		// requiredSyncs);
		while (synchCounterx == 0 && maxDelay-- > 0) {
			// Thread.sleep(100);
			wait(100 * TestCaseProperties.getScaling());
		}
		if (synchCounterx > 0) {
			synchCounterx = 0;
			fail("**** sync received");
		}
	}

	private synchronized boolean checkForAdditionalNotifications(int maxDelay)
			throws InterruptedException {
		while (synchCounterx == 0 && maxDelay-- > 0) {
			// Thread.sleep(100);
			wait(100 * TestCaseProperties.getScaling());
		}
		if (synchCounterx > 0) {
			System.out.println("Warning:Additional producer/consumer notification detected");
			synchCounterx = 0;
			return true;
		}
		return false;
	}

	synchronized void syncup(String pid) {
		if (getProperty("dump.now") != null) {
			System.out.println("Syncing up " + pid);
		}
		synchCounterx++;
		notify();
	}

	private synchronized void clearSync() {
		synchCounterx = 0;
	}

	/**
	 * The most basic tests: create and connect check if the methods of the Wire
	 * interface return a correct results check if the proper methods are called
	 * (producersConnected, consumersConnected)
	 */
	@Test
	public void testCreateWire() throws Exception {
		System.out.println("Must call producersConnected(..) for consumer.ConsumerImplA");
		helper.registerConsumer("consumer.ConsumerImplA", new Class[] {
				String.class, Integer.class, Float.class});
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplA");

		System.out.println("Must call consumersConnected(..) for producer.ProducerImplA");
		helper.registerProducer("producer.ProducerImplA", new Class[] {
				String.class, Float.class});
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplA", "");

		Hashtable<String,Object> properties = new Hashtable<>();
		properties.put("property1", Integer.valueOf(1));
		properties.put("property2", Float.valueOf(1.0f));
		properties.put("property3", Boolean.valueOf(false));

		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplA and producersConnected(..) for consumer.ConsumerImplA");
		Wire wire = helper.createWire(wa, "producer.ProducerImplA",
				"consumer.ConsumerImplA", properties);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplA");

		assertThat(wire
				.isValid()).as("Test wire.isValid after createWire: NOT OK. ").isTrue();
		assertThat(wire
				.isConnected()).as("Test wire.isConnected after createWire: NOT OK. ").isTrue();
		assertThat(compareWires_Flavors(wire.getFlavors(), new Class[] {
						String.class, Integer.class, Float.class})).as("Test wire flavors after createWire: NOT OK. ").isTrue();

		wire.update("NEW VALUE");

		assertThat(wire
				.getLastValue()).as("Test update wire: NOT OK. ").isEqualTo("NEW VALUE");

		Hashtable<String,Object> newProperties = new Hashtable<>();
		newProperties.put("property1", Integer.valueOf(2));
		newProperties.put("property2", Float.valueOf(2.0f));
		newProperties.put("property3", Boolean.valueOf(false));
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplA and producersConnected(..) for consumer.ConsumerImplA");
		helper.updateWire(wa, wire, newProperties);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplA");
		assertThat(compareWireProperties(wire.getProperties(), newProperties)).as("Test wire properties after updateWire: NOT OK.").isTrue();
	}

	/**
	 * Test behaviour of deleted wires
	 *
	 * isValid (should be false after deletion) isConnected (should be false
	 * after deletion) Wire.getFlavors (should return null) WireAdmin.getWires
	 * (should return null when filter matches the deleted wire)
	 */
	@Test
	public void testDeleteWire() throws Exception {
		System.out.println("creating two wires");
		Wire wire1 = helper.createWire(wa, "deletedWireProducer",
				"deletedWireConsumer", null);
		Wire wire2 = helper.createWire(wa, "deletedWireProducer",
				"deletedWireConsumer", null);
		System.out.println("registering consumer and producer so the wires are connected when we delete one of them later");
		helper.registerProducer("deletedWireProducer",
				new Class[] {java.lang.String.class});
		helper.registerConsumer("deletedWireConsumer",
				new Class[] {java.lang.String.class});
		/*
		 * The number of the notifications is 5 : 1 for "deletedWireProducer"
		 * with parameter null and 2x2 for each wire and each producer/consumer
		 */
		waitForSync(5, 200);
		checkForAdditionalNotifications(5);
		System.out.println("Deleting wire. Must call consumersConnected and producersConnected");
		wa.deleteWire(wire1);
		waitForSync(2, 200);
		Wire[] wires = returnedWires.get("deletedWireProducer");
		assertThat(wires).as("{deletedWireProducer} Invokes consumersConnected(..) NOT OK, Wrong Wire[]").isNotNull();
		assertThat(wires.length).as("{deletedWireProducer} Invokes consumersConnected(..) NOT OK, Wrong Wire[]").isOne();

		wires = returnedWires.get("deletedWireConsumer");
		assertThat(wires).as("{deletedWireConsumer} Invokes producersConnected(..) NOT OK, Wrong Wire[]").isNotNull();
		assertThat(wires.length).as("{deletedWireConsumer} Invokes producersConnected(..) NOT OK, Wrong Wire[]").isOne();
		System.out.println("Deleting the second wire.");
		wa.deleteWire(wire2);
		waitForSync(2, 200);
		compareConnected("deletedWireProducer", "deletedWireConsumer");
		assertThat(wire1.isValid()).as("Test Wire.isValid after deleteWire falied! Wire.isValid() returns true ").isFalse();
		assertThat(wire2.isValid()).as("Test Wire.isValid after deleteWire falied! Wire.isValid() returns true ").isFalse();
		assertThat(wire1.isConnected()).as("Test wire.isConnected after deleteWire failed! Wire.isConnected returns true").isFalse();
		assertThat(wire2.isConnected()).as("Test wire.isConnected after deleteWire failed! Wire.isConnected returns true").isFalse();
		assertThat(wire1
				.getFlavors()).as("Test wire flavors after deleteWire failed.").isNull();
		assertThat(wire2
				.getFlavors()).as("Test wire flavors after deleteWire failed.").isNull();
		wires = wa
				.getWires("(&(wireadmin.producer.pid=deletedWireProducer)(wireadmin.consumer.pid=deletedWireConsumer))");
		assertThat(wires).as("Test getWires after deleteWire failed.").isNull();
	}

	/**
	 * Simulate some "incorrect" situations
	 *
	 * Create a wire with non-registered pids (wire should be valid but not
	 * connected) Create a wire with null pid (behaviour not specified - could
	 * be ignored, however an Exception is expected by the test) Create a wire
	 * with invalid properties (Exception should be thrown) Create a wire with
	 * incompatible flavors (should be created but data should not be tranfered
	 * over it)
	 */
	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	@Test
	public void testIncorrectCreateWire() throws Exception {
		Wire wire = helper.createWire(wa, "aa.bb.cc", "cc.dd", null);
		assertThat(wire
				.isValid()).as("Test createWire with non-existing PIDs: NOT OK. ").isTrue();
		assertThat(wire
				.isConnected()).as("Test createWire with non-existing PIDs: NOT OK. ").isFalse();
		wa.deleteWire(wire);
		try {
			wire = helper.createWire(wa, null, "cc.dd", null);
			fail("Test createWire with null PIDs: NOT OK.");
		}
		catch (NullPointerException e) {
			System.out.println("Test createWire with null PIDs: Operation passed: OK. Exception thrown.");
		}
		catch (IllegalArgumentException e) {
			System.out.println("Test createWire with null PIDs: Operation passed: OK. Exception thrown.");
		}
		Hashtable<Object,Object> properties1 = new Hashtable<>();
		properties1.put(Integer.valueOf(1), "test");
		try {
			wire = helper.createWire(wa, "abc.bac", "bac.abc",
					(Map) properties1);
			fail("Test createWire with incorrect properties' key: NOT OK. ");
		}
		catch (IllegalArgumentException e) {
			System.out.println("Test createWire with incorrect properties' key: Operation passed: OK. Exception thrown.");
		}
		Hashtable<String,Object> properties2 = new Hashtable<>();
		properties2.put("Test", Integer.valueOf(1));
		properties2.put("test", Integer.valueOf(2));
		try {
			wire = helper.createWire(wa, "abc.bac", "bac.abc", properties2);
			fail("Test createWire with case insensensitive keys: NOT OK.");
		}
		catch (IllegalArgumentException e) {
			System.out.println("Test createWire with case insensensitive keys: Operation passed: OK. Exception thrown.");
		}
		System.out.println("Must call producersConnected(..) for consumer.ConsumerImplB");
		helper.registerConsumer("consumer.ConsumerImplB",
				new Class[] {Date.class});
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplB");
		System.out.println("Must call consumersConnected(..) for producer.ProducerImplA");
		helper.registerProducer("producer.ProducerImplA", new Class[] {
				String.class, Float.class});
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplA", "");
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplA and producersConected(..) for consumer.ConsumerImplB");
		wire = helper.createWire(wa, "producer.ProducerImplA",
				"consumer.ConsumerImplB", null);
		waitForSync(2, 200);
		checkForAdditionalNotifications(5); // check for possible
		// unneeded/undesired
		// producer/consumer notifications
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplB");
		assertThat(wire
				.isValid()).as("Test createWire with incompatible flavors: NOT OK.").isTrue();
		assertThat(wire
				.isConnected()).as("Test createWire with incompatible flavors: NOT OK.").isTrue();
		assertThat(notUpdated(wire)).as("Test createWire with incompatible flavors: NOT OK.").isTrue();
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplA and producersConected(..) for consumer.ConsumerImplB");
		wa.deleteWire(wire);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplB");
	}

	/**
	 * Simulate "incorrect" situations when wire is updated
	 *
	 * Update with invalid properties (Exception should be thrown) Update a
	 * deleted wire (not defined in the specification - the test expecs an
	 * Exception to be thrown)
	 */
	@SuppressWarnings({
			"unchecked", "rawtypes"
	})
	@Test
	public void testIncorrectUpdateWire() throws Exception {
		System.out.println("Must call producersConnected(..) for consumer.ConsumerImplA");
		helper.registerConsumer("consumer.ConsumerImplA", new Class[] {
				String.class, Integer.class, Float.class});
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplA");

		System.out.println("Must call consumersConnected(..) for producer.ProducerImplA");
		helper.registerProducer("producer.ProducerImplA", new Class[] {
				String.class, Float.class});
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplA", "");

		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplA and producersConected(..) for consumer.ConsumerImplA");
		Wire wire = helper.createWire(wa, "producer.ProducerImplA",
				"consumer.ConsumerImplA", null);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplA");

		Hashtable<Object,Object> properties2 = new Hashtable<>();
		properties2.put(Integer.valueOf(1), "TEST");
		try {
			helper.updateWire(wa, wire, (Map) properties2);
			fail("Test updateWire with incorrect properties' keys: NOT OK.");
		}
		catch (IllegalArgumentException e) {
			System.out.println("Test updateWire with incorrect properties' keys: Operation passed: OK.Exception thrown.");
		}
		Hashtable<String,Object> properties = new Hashtable<>();
		properties.put("property1", "TEST1");
		properties.put("PROPERTY1", "TEST2");
		try {
			helper.updateWire(wa, wire, properties);
			fail("Test updateWire with case insensitive properties' keys: NOT OK. Exception was not thrown");
		}
		catch (IllegalArgumentException e) {
			System.out.println("Test updateWire with case insensitive properties' keys: Operation passed: OK.Exception thrown.");
		}

		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplA and producersConected(..) for consumer.ConsumerImplA");
		wa.deleteWire(wire);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplA");
		properties = new Hashtable();
		properties.put("property", "correct");
		try {
			helper.updateWire(wa, wire, properties);
			assertThat(compareWireProperties(wire.getProperties(), properties)).as("Test updateWire with non- existing wire: NOT OK.").isFalse();
		}
		catch (IllegalArgumentException e) {
			System.out.println("Test updateWire with non-existing wire: Operation passed: OK.Exception thrown.");
		}
	}

	/**
	 * Test getting a wire with incoorect filter (Exception should be thrown
	 */
	@Test
	public void testIncorrectGetWires() throws Exception {
		try {
			wa.getWires("asdasd=dd");
			fail("Test getWires with invalid filter: NOT OK.");
		}
		catch (InvalidSyntaxException e) {
			System.out.println("Test getWires with invalid filter: Operation passed: OK. Exception thrown.");
		}
	}

	/**
	 * Test what happens with the wire when consumer/producers are
	 * registered/unregistered Wire should remain persistent after such
	 * operation
	 */
	@Test
	public void testRegisterUnregisterConsumerProducer() throws Exception {
		System.out.println("Must call producersConnected(..) for consumer.ConsumerImplC");
		helper.registerConsumer("consumer.ConsumerImplC", new Class[] {
				String.class, Integer.class, Float.class});
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplC");
		System.out.println("Must call consumersConnected(..) for producer.ProducerImplC");
		helper.registerProducer("producer.ProducerImplC", new Class[] {
				String.class, Float.class, Boolean.class});
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplC", "");
		Hashtable<String,Object> properties = new Hashtable<>();
		properties.put("property1", new String("TEST"));
		properties.put("property2", Float.valueOf(5.0f));
		properties.put("property3", Boolean.valueOf(false));
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplC and producersConnected(..) for consumer.ConsumerImplC");
		Wire wire = helper.createWire(wa, "producer.ProducerImplC",
				"consumer.ConsumerImplC", properties);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplC");
		Dictionary<String,Object> oldProperties = wire.getProperties();
		System.out.println("Must call producersConnected(..) for producer.ProducerImplC");
		helper.unregisterProducer("producer.ProducerImplC");
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplC");
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplC and producersConnected(..) for consumer.ConsumerImplC");
		helper.registerProducer("producer.ProducerImplC", new Class[] {
				String.class, Float.class, Boolean.class});
		waitForSync(2, 100);
		checkForAdditionalNotifications(5); // check for possible
		// unneeded/undesired
		// producer/consumer notifications
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplC");
		wire = wa
				.getWires("(&(wireadmin.producer.pid=producer.ProducerImplC)(wireadmin.consumer.pid=consumer.ConsumerImplC))")[0];
		assertThat(compareWireProperties(wire.getProperties(), oldProperties)).as("Test wire properties after Producer's re- registration: NOT OK.").isTrue();

		oldProperties = wire.getProperties();
		System.out.println("Must call consumersConnected(..) for producer.ProducerImplC");
		helper.unregisterConsumer("consumer.ConsumerImplC");
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplC", "");
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplC and producersConnected(..) for consumer.ConsumerImplC");
		helper.registerConsumer("consumer.ConsumerImplC", new Class[] {
				String.class, Integer.class, Float.class});
		waitForSync(2, 100);
		checkForAdditionalNotifications(5); // check for possible
		// unneeded/undesired
		// producer/consumer notifications
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplC");
		wire = wa
				.getWires("(&(wireadmin.producer.pid=producer.ProducerImplC)(wireadmin.consumer.pid=consumer.ConsumerImplC))")[0];
		assertThat(compareWireProperties(wire.getProperties(), oldProperties)).as("Test wire properties after Consumer's re- registration: NOT OK.").isTrue();
	}

	/**
	 * Test updating a wire, befor/after its consumer or producer was
	 * unregistered operation should be valid
	 */
	@Test
	public void testUpdateAfterUnregister() throws Exception {
		System.out.println("Must call producersConnected(..) for consumer.ConsumerImplC");
		helper.registerConsumer("consumer.ConsumerImplC", new Class[] {
				String.class, Integer.class, Float.class});
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplC");
		System.out.println("Must call consumersConnected(..) for producer.ProducerImplC");
		helper.registerProducer("producer.ProducerImplC", new Class[] {
				String.class, Float.class, Boolean.class});
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplC", "");
		Hashtable<String,Object> properties = new Hashtable<>();
		properties.put("property1", new String("TEST"));
		properties.put("property2", Float.valueOf(5.0f));
		properties.put("property3", Boolean.valueOf(false));
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplC and producersConnected(..) for consumer.ConsumerImplC");
		Wire wire = helper.createWire(wa, "producer.ProducerImplC",
				"consumer.ConsumerImplC", properties);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplC");

		wire = wa
				.getWires("(&(wireadmin.producer.pid=producer.ProducerImplC)(wireadmin.consumer.pid=consumer.ConsumerImplC))")[0];
		System.out.println("Must call producersConnected(..) for consumer.ConsumerImplC");
		helper.unregisterProducer("producer.ProducerImplC");
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplC");
		properties = new Hashtable<>();
		properties.put("property1", new String("NEW TEST FOR PRODUCER"));
		properties.put("property2", Float.valueOf(2.0f));
		helper.updateWire(wa, wire, properties);

		waitForNoSync(30);

		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplC and producersConnected(..) for consumer.ConsumerImplC");
		helper.registerProducer("producer.ProducerImplC", new Class[] {
				String.class, Float.class, Boolean.class});
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplC");
		wire = wa
				.getWires("(&(wireadmin.producer.pid=producer.ProducerImplC)(wireadmin.consumer.pid=consumer.ConsumerImplC))")[0];
		assertThat(compareWireProperties(wire.getProperties(), properties)).as("Test updateWire with unregistered Producer: NOT OK.").isTrue();
		System.out.println("Must call consumersConnected(..) for producer.ProducerImplC");
		helper.unregisterConsumer("consumer.ConsumerImplC");
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplC", "");
		properties = new Hashtable<>();
		properties.put("property1", new String("NEW TEST FOR CONSUMER"));
		properties.put("property2", Float.valueOf(1.0f));
		helper.updateWire(wa, wire, properties);

		waitForNoSync(30);

		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplC and producersConnected(..) for consumer.ConsumerImplC");
		helper.registerConsumer("consumer.ConsumerImplC", new Class[] {
				String.class, Integer.class, Float.class});
		waitForSync(2, 100);
		checkForAdditionalNotifications(5); // check for possible
		// unneeded/undesired
		// producer/consumer notifications
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplC");
		wire = wa
				.getWires("(&(wireadmin.producer.pid=producer.ProducerImplC)(wireadmin.consumer.pid=consumer.ConsumerImplC))")[0];
		assertThat(compareWireProperties(wire.getProperties(), properties)).as("Test updateWire with unregistered Consumer: NOT OK.").isTrue();
	}

	/**
	 * Test Wire persistency after restarting the WireAdmin bundle
	 */
	@Test
	public void testRestartWireadmin() throws Exception {
		System.out.println("createing first wire");
		Wire wire1 = helper.createWire(wa, "producer1", "consumer1", null);
		System.out.println("createing second wire");
		Wire wire2 = helper.createWire(wa, "producer2", "consumer2", null);
		System.out.println("registering producer and consumer for first wire");
		helper.registerProducer("producer1",
				new Class[] {java.lang.String.class});
		helper.registerConsumer("consumer1",
				new Class[] {java.lang.String.class});
		System.out.println("stopping wire admin bundle");
		Bundle wireadmin = getServiceReference(wa).getBundle();
		ungetService(wa);
		wireadmin.stop();

		// The test must wait several seconds.
		// The total number of consumers and producers could vary therefore
		// it cannot count on the number of notifications
		try {
			Thread.sleep(5000 * TestCaseProperties.getScaling());
		}
		catch (InterruptedException ie) {
			// ignored
		}

		System.out.println("registering producer and consumer for the second wire");
		helper.registerProducer("producer2",
				new Class[] {java.lang.String.class});
		helper.registerConsumer("consumer2",
				new Class[] {java.lang.String.class});
		System.out.println("starting wire admin bundle again");
		System.out.println("Must call consumersConnected(..) for: producer1, producer2");
		System.out.println("and producersConnected(..) for: consumer1, consumer2");

		clearSync(); // reset the synch counter
		wireadmin.start();
		// The test must wait several seconds.
		// The total number of consumers and producers could vary therefore
		// it cannot count on the number of notifications
		try {
			Thread.sleep(5000 * TestCaseProperties.getScaling());
		}
		catch (InterruptedException ie) {
			// ignored
		}

		wa = getService(WireAdmin.class);
		compareConnected("producer1", "consumer1");
		compareConnected("producer2", "consumer2");
		Wire[] wires = wa
				.getWires("(&(wireadmin.producer.pid=producer1)(wireadmin.consumer.pid=consumer1))");
		assertThat(wires).as("Test wire state after WireAdmin restarting: Wire is null.").isNotNull();
		wire1 = wires[0];
		assertThat(wire1).as("Test wire state after WireAdmin restart: first wire isn't valid or connected.").isNotNull();
		assertThat(wire1.isValid()).as("Test wire state after WireAdmin restart: first wire isn't valid or connected.").isTrue();
		assertThat(wire1.isConnected()).as("Test wire state after WireAdmin restart: first wire isn't valid or connected.").isTrue();

		wires = wa
				.getWires("(&(wireadmin.producer.pid=producer2)(wireadmin.consumer.pid=consumer2))");
		assertThat(wires).as("Test wire state after WireAdmin restarting: Wire is null.").isNotNull();
		wire2 = wires[0];
		assertThat(wire2).as("Test wire state after WireAdmin restart: first wire isn't valid or connected.").isNotNull();
		assertThat(wire2.isValid()).as("Test wire state after WireAdmin restart: first wire isn't valid or connected.").isTrue();
		assertThat(wire2.isConnected()).as("Test wire state after WireAdmin restart: first wire isn't valid or connected.").isTrue();
	}

	/**
	 * Test if Wire.getFlavors when flavors aren't Class[] returns null
	 */
	@Test
	public void testGetFlavors() throws Exception {
		helper.registerProducer("producer.ProducerImplC", new Class[] {
				String.class, Float.class, Boolean.class});
		waitForSync(1, 100);
		compareConnected("producer.ProducerImplC", "");
		helper.registerConsumer("consumer.ConsumerImplF", new String[] {"AA",
				"BB"});
		waitForSync(1, 100);
		compareConnected("", "consumer.ConsumerImplF");
		Wire wire1 = helper.createWire(wa, "producer.ProducerImplC",
				"consumer.ConsumerImplF", null);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplF");
		assertThat(wire1.getFlavors()).as("Test wire getFlavors with incorrect consumer's flavors: NOT OK.").isNull();
	}

	/**
	 * Test producer/consumers taking part in several wires
	 */
	@Test
	public void testMultipleRegistration() throws Exception {
		helper.registerConsumer("consumer.ConsumerImplA", new Class[] {
				String.class, Integer.class, Float.class});
		helper.registerConsumer("consumer.ConsumerImplC", new Class[] {
				String.class, Integer.class, Float.class});
		helper.registerProducer("producer.ProducerImplA", new Class[] {
				String.class, Float.class});
		helper.registerProducer("producer.ProducerImplC", new Class[] {
				String.class, Float.class, Boolean.class});

		// wait for producers/consumersConnected(null) calls when being
		// registered without existing wires
		waitForSync(4, 100);
		checkForAdditionalNotifications(5); // check for possible
		// unneeded/undesired
		// producer/consumer notifications

		Hashtable<String,Object> properties = new Hashtable<>();
		properties.put("property1", Integer.valueOf(1));
		properties.put("property2", Float.valueOf(1.0f));
		properties.put("property3", Boolean.valueOf(false));
		@SuppressWarnings("unused")
		Wire wire = helper.createWire(wa, "producer.ProducerImplA",
				"consumer.ConsumerImplA", properties);
		wire = helper.createWire(wa, "producer.ProducerImplC",
				"consumer.ConsumerImplC", properties);
		waitForSync(4, 100);
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplA and producersConnected(..) for consumer.ConsumerImplC");
		wire = helper.createWire(wa, "producer.ProducerImplA",
				"consumer.ConsumerImplC", null);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplC");
		System.out.println("Must call both consumersConnected(..) for producer.ProducerImplC and producersConnected(..) for consumer.ConsumerImplA");
		wire = helper.createWire(wa, "producer.ProducerImplC",
				"consumer.ConsumerImplA", null);
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplA");
		Wire[] correct = wa.getWires("(org.osgi.test.wireadmin=" + testMethodName
				+ ")");
		System.out.println("Must call producersConnected(..) for: consumer.ConsumerImplA and consumer.ConsumerImplC");
		helper.unregisterProducer("producer.ProducerImplA");
		waitForSync(2, 100);
		compareConnected("", "consumer.ConsumerImplA");
		compareConnected("", "consumer.ConsumerImplC");
		assertThat(compareWires_Flavors(correct,
						wa.getWires("(org.osgi.test.wireadmin=" + testMethodName
								+ ")"))).as("Test Wireadmin Wires after Producer's unregistration: NOT OK.").isTrue();
		System.out.println("Must call consumersConnected(..) for: producer.ProducerImplA");
		System.out.println("and producersConnected(..) for: consumer.ConsumerImplA and consumer.ConsumerImplC");
		helper.registerProducer("producer.ProducerImplA", new Class[] {
				String.class, Float.class});
		waitForSync(4, 100);
		checkForAdditionalNotifications(5); // check for possible
		// unneeded/undesired
		// producer/consumer notifications
		compareConnected("producer.ProducerImplA", "consumer.ConsumerImplA");
		compareConnected("", "consumer.ConsumerImplC");
		assertThat(compareWires_Flavors(correct,
						wa.getWires("(org.osgi.test.wireadmin=" + testMethodName
								+ ")"))).as("Test Wireadmin Wires after Producer's registration: NOT OK.").isTrue();
		System.out.println("Must call consumersConnected(..) for: producer.ProducerImplA and producer.ProducerImplC");
		helper.unregisterConsumer("consumer.ConsumerImplC");
		waitForSync(2, 100);
		compareConnected("producer.ProducerImplA", "");
		compareConnected("producer.ProducerImplC", "");
		assertThat(compareWires_Flavors(correct,
						wa.getWires("(org.osgi.test.wireadmin=" + testMethodName
								+ ")"))).as("Test Wireadmin Wires after Consumer's unregistration: NOT OK.").isTrue();
		System.out.println("Must call consumersConnected(..) for: producer.ProducerImplA and producer.ProducerImplC");
		System.out.println("and producersConnected(..) for: consumer.ConsumerImplC");
		helper.registerConsumer("consumer.ConsumerImplC", new Class[] {
				String.class, Integer.class, Float.class});
		waitForSync(4, 100);
		checkForAdditionalNotifications(5); // check for possible
		// unneeded/undesired
		// producer/consumer notifications
		compareConnected("producer.ProducerImplA", "");
		compareConnected("producer.ProducerImplC", "consumer.ConsumerImplC");
		assertThat(compareWires_Flavors(correct,
						wa.getWires("(org.osgi.test.wireadmin=" + testMethodName
								+ ")"))).as("Test Wireadmin Wires after Consumer's registration: NOT OK.").isTrue();
	}

	/**
	 * Creates a wire, binds it and checks its poll and update methods
	 */
	@Test
	public void testPollUpdate() throws Exception {
		System.out.println("create wire for the test");
		Hashtable<String,Object> h = new Hashtable<>();
		h.put("org.osgi.test.wireadmin.property", "42");
		Wire localWire = helper.createWire(wa, "wireAPITest.producer.pid",
				"wireAPITest.consumer.pid", h);
		// check if the wire is connected
		assertThat(localWire.isConnected()).as("wire is connected").isFalse();
		// poll unconnected wire
		System.out.println("polling unconnected wire");
		Object value = localWire.poll();
		assertThat(value).as("received value (null) ").isNull();
		// update unconnected wire
		System.out.println("updating unconnected wire");
		localWire.update(Integer.valueOf(4242));
		// register producer
		System.out.println("register test producer");
		WireAPITestProducerImpl producer = new WireAPITestProducerImpl();
		helper.registerProducer(producer, "wireAPITest.producer.pid",
				new Class[] {Integer.class}, null);
		System.out.println("register test consumer");
		WireAPITestConsumerImpl consumer = new WireAPITestConsumerImpl();
		helper.registerConsumer(consumer, "wireAPITest.consumer.pid",
				new Class[] {Integer.class}, null);
		int count = 0;
		while (!localWire.isConnected() && (count++ < 100)) {
			Thread.sleep(50);
		}
		// check if the wire is connected
		assertThat(localWire.isConnected()).as("wire is NOT connected").isTrue();
		// poll
		System.out.println("polling connected wire");
		value = localWire.poll();
		assertThat(value).as("did not receive value Integer(42) ").isEqualTo(Integer.valueOf(42));
		// update
		System.out.println("updating connected wire");
		localWire.update(Integer.valueOf(4242));
		assertThat(consumer.getValue()).as("did not receive value Integer(4242) ").isEqualTo(Integer.valueOf(4242));
		// poll wire with producer throwing exception in its polled method
		System.out.println("polling connected wire with producer throwing exception");
		producer.setThrowsException(true);
		value = localWire.poll();
		assertThat(value).as("received value (null) ").isNull();
		producer.setThrowsException(false);
	}

	/**
	 * Tests wire flow control
	 */
	private final String	delimiter	= "------------------------------------------------------------";

	@Test
	public void testValueFilteringCurrentPrevious() throws Exception {
		final String producerPid = "filter.test.producer.pid." + testMethodName;
		final String consumerPid = "filter.test.conusmer.pid." + testMethodName;
		String filter = "(" + WireConstants.WIREVALUE_CURRENT + "=5)";
		// create wire
		System.out.println("create wire for the test");
		Hashtable<String,Object> h = new Hashtable<>();
		h.put("org.osgi.test.wireadmin.property", "42");
		h.put(WireConstants.WIREADMIN_FILTER, filter);
		Wire localWire = helper.createWire(wa, producerPid, consumerPid, h);
		// register producer
		System.out.println("register test producer");
		FilteredProducerImpl fpi = new FilteredProducerImpl();
		helper.registerProducer(fpi, producerPid,
				new Class[] {Integer.class}, null);
		// register consumer
		System.out.println("register test consumer");
		FilteredConsumerImpl fci = new FilteredConsumerImpl();
		helper.registerConsumer(fci, consumerPid,
				new Class[] {Integer.class}, null);
		// wait until filtered producer's method consumersConnected is called
		// with the test wire
		int i = 0;
		while ((fpi.getWire() == null) && (i++ < 500)) {
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException e) {
				// ignored
			}
		}
		if (fpi.getWire() == null) {
			fail("producer not connected (wire is null)");
		}
		// current value test - filter is set on wire creation
		System.out.println("Current value test - notifying producer. Will send integers [0..9]");
		System.out.println("Filter is " + filter);
		System.out.println("Consumer should receive values [5]");
		fpi.updateWire(1);
		int counter = 0;
		while ((counter++ < 100) && (fci.numberValuesReceived() < 1)) {
			Thread.sleep(50);
		}
		List<Object> values = fci.resetValuesReceived();
		assertThat(values).as("incorrect values received").isEqualTo(Arrays
				.asList(new Integer[] {Integer.valueOf(5)}));
		System.out.println(delimiter);
		// previous value test
		// last received value was 5 so the consumer should receive 0 and
		// nothing else
		filter = "(" + WireConstants.WIREVALUE_PREVIOUS + "=5)";
		h.put(WireConstants.WIREADMIN_FILTER, filter);
		helper.updateWire(wa, localWire, h);
		System.out.println("Previous value test - notifying producer. Will send integers [0..9]");
		System.out.println("Filter is " + filter);
		System.out.println("Consumer should receive values [0]");
		fpi.updateWire(1);
		counter = 0;
		while ((counter++ < 100) && (fci.numberValuesReceived() < 1)) {
			Thread.sleep(50);
		}
		values = fci.resetValuesReceived();
		assertThat(values).as("incorrect values received").isEqualTo(Arrays
				.asList(new Integer[] {Integer.valueOf(0)}));
		System.out.println(delimiter);
	}

	@Test
	public void testValueFilteringDeltaAbsolute() throws Exception {
		final String producerPid = "filter.test.producer.pid." + testMethodName;
		final String consumerPid = "filter.test.conusmer.pid." + testMethodName;
		// delta absolute test
		// last received value was 0 so we should receive:
		// 4 - absolute delta 4
		// 8 - absolute delta 8
		String filter = "(" + WireConstants.WIREVALUE_DELTA_ABSOLUTE + ">=3)";
		// create wire
		System.out.println("create wire for the test");
		Hashtable<String,Object> h = new Hashtable<>();
		h.put("org.osgi.test.wireadmin.property", "42");
		h.put(WireConstants.WIREADMIN_FILTER, filter);
		@SuppressWarnings("unused")
		Wire localWire = helper.createWire(wa, producerPid, consumerPid, h);
		// register producer
		System.out.println("register test producer");
		FilteredProducerImpl fpi = new FilteredProducerImpl();
		helper.registerProducer(fpi, producerPid,
				new Class[] {Integer.class}, null);
		// register consumer
		System.out.println("register test consumer");
		FilteredConsumerImpl fci = new FilteredConsumerImpl();
		helper.registerConsumer(fci, consumerPid,
				new Class[] {Integer.class}, null);
		// wait until filtered producer's method consumersConnected is called
		// with the test wire
		int i = 0;
		while ((fpi.getWire() == null) && (i++ < 500)) {
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException e) {
				// ignored
			}
		}
		if (fpi.getWire() == null) {
			fail("producer not connected (wire is null)");
		}
		// delta absolute and delta relative tests need the delta increased to 2
		System.out.println("Value delta absolute test - notifying producer. Will send integers [0, 2, 4, 6, 8]");
		System.out.println("Filter is " + filter);
		System.out.println("Consumer should receive values [4, 8]");
		fpi.updateWire(2);
		int counter = 0;
		while ((counter++ < 100) && (fci.numberValuesReceived() < 2)) {
			Thread.sleep(50);
		}
		List<Object> values = fci.resetValuesReceived();
		assertThat(values).as("incorrect values received").isEqualTo(Arrays.asList(new Integer[] {
				Integer.valueOf(4), Integer.valueOf(8)}));
		System.out.println(delimiter);
	}

	@Test
	public void testValueFilteringDeltaRelative() throws Exception {
		final String producerPid = "filter.test.producer.pid." + testMethodName;
		final String consumerPid = "filter.test.conusmer.pid." + testMethodName;
		// delta relative test
		// last received value was 8 so we should receive
		/* !!! last received value changes dinamicly during that test !!! */
		// 0 - relative delta Infinity
		// 2 - relative delta 1
		// 4 - relative delta 0.5
		// 8 - relative delta 0.5
		String filter = "(" + WireConstants.WIREVALUE_DELTA_RELATIVE + ">=0.4)";
		// create wire
		System.out.println("create wire for the test");
		Hashtable<String,Object> h = new Hashtable<>();
		h.put("org.osgi.test.wireadmin.property", "42");
		h.put(WireConstants.WIREADMIN_FILTER, filter);
		@SuppressWarnings("unused")
		Wire localWire = helper.createWire(wa, producerPid, consumerPid, h);
		// register producer
		System.out.println("register test producer");
		FilteredProducerImpl fpi = new FilteredProducerImpl();
		helper.registerProducer(fpi, producerPid,
				new Class[] {Integer.class}, null);
		// register consumer
		System.out.println("register test consumer");
		FilteredConsumerImpl fci = new FilteredConsumerImpl();
		helper.registerConsumer(fci, consumerPid,
				new Class[] {Integer.class}, null);
		// wait until filtered producer's method consumersConnected is called
		// with the test wire
		int i = 0;
		while ((fpi.getWire() == null) && (i++ < 500)) {
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException e) {
				// ignored
			}
		}
		if (fpi.getWire() == null) {
			fail("producer not connected (wire is null)");
		}
		System.out.println("Value delta relative test - notifying producer. Will send integers [0, 2, 4, 6, 8]");
		System.out.println("Filter is " + filter);
		System.out.println("Consumer should receive values [0, 2, 4, 8]");
		fpi.updateWire(2);
		int counter = 0;
		while ((counter++ < 100) && (fci.numberValuesReceived() < 4)) {
			Thread.sleep(50);
		}
		List<Object> values = fci.resetValuesReceived();
		assertThat(values).as("incorrect values received").isEqualTo(Arrays
				.asList(new Integer[] {Integer.valueOf(0), Integer.valueOf(2),
						Integer.valueOf(4), Integer.valueOf(8)}));
		System.out.println(delimiter);
	}

	@Test
	public void testValueFilteringElapsed() throws Exception {
		final String producerPid = "filter.test.producer.pid." + testMethodName;
		final String consumerPid = "filter.test.conusmer.pid." + testMethodName;
		// time elapsed test
		// 4 should be received since it is the first one that has waited so
		// long
		String filter = "(" + WireConstants.WIREVALUE_ELAPSED + ">=1000)";
		// create wire
		System.out.println("create wire for the test");
		Hashtable<String,Object> h = new Hashtable<>();
		h.put("org.osgi.test.wireadmin.property", "42");
		Wire localWire = helper.createWire(wa, producerPid, consumerPid, h);
		// register producer
		System.out.println("register test producer");
		FilteredProducerImpl fpi = new FilteredProducerImpl();
		helper.registerProducer(fpi, producerPid,
				new Class[] {Integer.class}, null);
		// register consumer
		System.out.println("register test consumer");
		FilteredConsumerImpl fci = new FilteredConsumerImpl();
		helper.registerConsumer(fci, consumerPid,
				new Class[] {Integer.class}, null);
		// wait until filtered producer's method consumersConnected is called
		// with the test wire
		int i = 0;
		while ((fpi.getWire() == null) && (i++ < 500)) {
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException e) {
				// ignored
			}
		}
		if (fpi.getWire() == null) {
			fail("producer not connected (wire is null)");
		}
		fpi.updateWire(2);
		int counter = 0;
		while ((counter++ < 100) && (fci.numberValuesReceived() < 5)) {
			Thread.sleep(50);
		}
		fci.resetValuesReceived(); // clear values
		h.put(WireConstants.WIREADMIN_FILTER, filter);
		helper.updateWire(wa, localWire, h);
		System.out.println("Time elapsed test - notifying producer. Will send integers [0, 2, 4, 6, 8]");
		System.out.println("Filter is " + filter);
		System.out.println("Consumer should receive values [2, 4, 6, 8]");
		// make the producer wait between sending updates
		fpi.updateWireDelayed(2, 1200);
		counter = 0;
		while ((counter++ < 100) && (fci.numberValuesReceived() < 4)) {
			Thread.sleep(50);
		}
		List<Object> values = fci.resetValuesReceived();
		assertThat(values).as("incorrect values received").isEqualTo(Arrays.asList(new Integer[] {Integer.valueOf(2), Integer.valueOf(4),
						Integer.valueOf(6), Integer.valueOf(8)}));
		System.out.println(delimiter);
	}

	@Test
	public void testValueFilteringDisabled() throws Exception {
		final String producerPid = "filter.test.producer.pid." + testMethodName;
		final String consumerPid = "filter.test.conusmer.pid." + testMethodName;
		String filter = "(" + WireConstants.WIREVALUE_CURRENT + ">=5)";
		// create wire
		System.out.println("create wire for the test");
		Hashtable<String,Object> h = new Hashtable<>();
		h.put("org.osgi.test.wireadmin.property", "42");
		h.put(WireConstants.WIREADMIN_FILTER, filter);
		@SuppressWarnings("unused")
		Wire localWire = helper.createWire(wa, producerPid, consumerPid, h);
		// register producer
		System.out.println("register test producer");
		FilteredProducerImpl fpi = new FilteredProducerImpl();
		Map<String,Object> p_h = new HashMap<>();
		p_h.put(WireConstants.WIREADMIN_PRODUCER_FILTERS, new Object());
		helper.registerProducer(fpi, producerPid, new Class[] {Integer.class},
				p_h);
		// register consumer
		System.out.println("register test consumer");
		FilteredConsumerImpl fci = new FilteredConsumerImpl();
		helper.registerConsumer(fci, consumerPid,
				new Class[] {Integer.class}, null);
		// wait until filtered producer's method consumersConnected is called
		// with the test wire
		int i = 0;
		while ((fpi.getWire() == null) && (i++ < 500)) {
			try {
				Thread.sleep(20);
			}
			catch (InterruptedException e) {
				// ignored
			}
		}
		if (fpi.getWire() == null) {
			fail("producer not connected (wire is null)");
		}
		// disable filtering test
		System.out.println("update the producer properties - add WIREADMIN_PRODUCER_FILTERS property");
		System.out.println("Filtering disabled test - notifying producer. Will send integers [0, 3, 6, 9]");
		System.out.println("Filter is " + filter);
		System.out.println("Consumer should receive values [0, 3, 6, 9]");
		fpi.updateWire(3);
		int counter = 0;
		while ((counter++ < 100) && (fci.numberValuesReceived() < 4)) {
			Thread.sleep(50);
		}
		List<Object> values = fci.resetValuesReceived();
		assertThat(values).as("incorrect values received").isEqualTo(Arrays
				.asList(new Integer[] {Integer.valueOf(0), Integer.valueOf(3),
						Integer.valueOf(6), Integer.valueOf(9)}));
		System.out.println(delimiter);
	}

	/**
	 * Tests event dispatching in the wire admin
	 */
	@Test
	public void testEvents() throws Exception {
		// a full mask
		Integer mask = Integer.valueOf(0xFFFFFFFF);
		// register the listeners
		TestWireAdminListener listener = new TestWireAdminListener(this, false); // should
		// receive
		// all
		// events
		TestWireAdminListener dummy = new TestWireAdminListener(this, true); // shouldn't
		// receive
		// events
		// real listener
		Hashtable<String,Object> walProps = new Hashtable<>();
		walProps.put(WireConstants.WIREADMIN_EVENTS, mask);
		ServiceRegistration<WireAdminListener> listenerReg = context
				.registerService(WireAdminListener.class, listener, walProps);
		// dummy listener
		Hashtable<String,Object> dummyProps = new Hashtable<>();
		dummyProps.put(WireConstants.WIREADMIN_EVENTS, Integer.valueOf(0));
		ServiceRegistration<WireAdminListener> dummyReg = context
				.registerService(WireAdminListener.class, dummy, dummyProps);
		// real test
		Hashtable<String,Object> h = new Hashtable<>();
		h.put("org.osgi.test.wireadmin.property", "42");
		System.out.println("Create a wire: WIRE_CREATED event is expected");
		Wire w = helper.createWire(wa, "producer.event.test.pid",
				"consumer.event.test.pid", h);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		List<Object> result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {Integer.valueOf(
						WireAdminEvent.WIRE_CREATED)}));

		System.out.println("update a wire: WIRE_UPDATED event is expected");
		helper.updateWire(wa, w, h);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {Integer.valueOf(
						WireAdminEvent.WIRE_UPDATED)}));

		System.out.println("connect a wire: WIRE_CONNECTED event is expected");
		helper.registerEventConsumer(false);
		helper.registerEventProducer(false);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {Integer.valueOf(
						WireAdminEvent.WIRE_CONNECTED)}));

		System.out.println("poll the wire: WIRE_TRACE event is expected");
		w.poll();
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays
				.asList(new Object[] {Integer.valueOf(WireAdminEvent.WIRE_TRACE)}));

		System.out.println("disconnect wire: WIRE_DISCONNECTED event is expected");
		helper.unregisterEventConsumer();
		helper.unregisterEventProducer();
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays
				.asList(new Object[] {Integer.valueOf(
						WireAdminEvent.WIRE_DISCONNECTED)}));

		// receive only PRODUCERS_EXCEPTION, CONSUMERS_EXCEPTION and
		// WIRE_DELETED events from now
		mask = Integer.valueOf(WireAdminEvent.CONSUMER_EXCEPTION
				| WireAdminEvent.PRODUCER_EXCEPTION
				| WireAdminEvent.WIRE_DELETED);
		walProps.put(WireConstants.WIREADMIN_EVENTS, mask);
		listenerReg.setProperties(walProps);

		System.out.println("cause exception from producersConnected: CONSUMER_EXCEPTION is expected");
		helper.registerEventProducer(false);
		helper.registerEventConsumer(true);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {
				Integer.valueOf(WireAdminEvent.CONSUMER_EXCEPTION), "testing"}));

		System.out.println("cause exception from updated: CONSUMER_EXCEPTION is expected");
		w.update("42");
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {
				Integer.valueOf(WireAdminEvent.CONSUMER_EXCEPTION), "testing"}));

		helper.unregisterEventConsumer();
		helper.unregisterEventProducer();

		System.out.println("cause exception from consumersConnected: PRODUCER_EXCEPTION is expected");
		helper.registerEventConsumer(false);
		helper.registerEventProducer(true);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {
				Integer.valueOf(WireAdminEvent.PRODUCER_EXCEPTION), "testing"}));

		System.out.println("cause exception from polled: PRODUCER_EXCEPTION is expected");
		w.poll();
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {
				Integer.valueOf(WireAdminEvent.PRODUCER_EXCEPTION), "testing"}));

		helper.unregisterEventConsumer();
		helper.unregisterEventProducer();

		System.out.println("delete wire: WIRE_DELETED event is expected");
		wa.deleteWire(w);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays.asList(new Object[] {Integer.valueOf(
						WireAdminEvent.WIRE_DELETED)}));

		System.out.println("cause exception form producersConnected of a 'free' Consumer. CONSUMER_EXCEPTION is expected");
		helper.registerEventConsumer(true);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays
				.asList(new Object[] {"testing"}));

		System.out.println("cause exception form consumersConnected of a 'free' Producer. PRODUCER_EXCEPTION is expected");
		helper.registerEventProducer(true);
		listener.waitForCall(5000 * TestCaseProperties.getScaling());
		result = listener.resetValuesReceived();
		assertThat(result).as("incorrect values received").isEqualTo(Arrays
				.asList(new Object[] {"testing"}));

		helper.unregisterEventConsumer();
		helper.unregisterEventProducer();
		listenerReg.unregister();
		dummyReg.unregister();
		result = dummy.resetValuesReceived();
		assertThat(result).as("dummy listener was called").isEqualTo(Collections.EMPTY_LIST);
	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// //////////////////////////////////////////wire admin test methods end
	// here ////////////////////////////////////////////
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ///////////////////////////////////////state manager test methods start
	// here //////////////////////////////////////////
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	@Test
	public void testNullScopes() throws Exception {
		Bundle consumer = installer.installBundle(context.getBundle().getEntry("tb1.jar"), true);
		Bundle producer = installer.installBundle(context.getBundle().getEntry("tb4.jar"), true);
		try {
			Wire wire = helper.createWire(wa, "producer.ProducerImplA",
					"consumer.ConsumerImplA", null);
			assertThat(wire
					.getScope()).as("Test Wire Scope without WirePermission: NOT OK.").isNull();

			wa.deleteWire(wire);

			setWirePermissions(new String[] {"*"}, consumer, true);
			wire = helper.createWire(wa, "producer.ProducerImplA",
					"consumer.ConsumerImplA", null);
			assertThat(compareScopes(wire.getScope(), null)).as("Test Wire Scope with null scope, after setting WirePermissions: NOT OK.").isTrue();
			BasicEnvelope envelope = new BasicEnvelope("true", "door1", "A");
			wire.update(envelope);
			assertThat(wire.getLastValue()).as("Test Wire update with null scope: NOT OK.").isEqualTo(envelope);
		}
		finally {
			consumer.uninstall();
			producer.uninstall();
		}
	}

	@Test
	public void testConsumerScope() throws Exception {
		Bundle consumer = installer.installBundle(context.getBundle().getEntry("tb2.jar"), true);
		Bundle producer = installer.installBundle(context.getBundle().getEntry("tb4.jar"), true);
		try {
			Wire wire = helper.createWire(wa, "producer.ProducerImplA",
					"consumer.ConsumerImplB", null);
			assertThat(wire.getScope()).as("Test Wire Scope with null producer scope without WirePermission: NOT OK.").isNull();
			wa.deleteWire(wire);
			setWirePermissions(new String[] {"*"}, consumer, true);
			setWirePermissions(new String[] {"*"}, producer, false);
			wire = helper.createWire(wa, "producer.ProducerImplA",
					"consumer.ConsumerImplB", null);
			assertThat(compareScopes(wire.getScope(), null)).as("Test Wire Scope with null producer scope, after setting WirePermission: NOT OK.").isTrue();
			BasicEnvelope envelope = new BasicEnvelope(Integer.valueOf(42),
					"window1", "A");
			wire.update(envelope);

			assertThat(wire.getLastValue()).as("Test Wire update with null scope: NOT OK.").isEqualTo(envelope);
		}
		finally {
			consumer.uninstall();
			producer.uninstall();
		}
	}

	@Test
	public void testAllScope() throws Exception {
		Bundle consumer = installer.installBundle(context.getBundle().getEntry("tb2.jar"), true);
		Bundle producer = installer.installBundle(context.getBundle().getEntry("tb5.jar"), true);
		try {
			Wire wire = helper.createWire(wa, "producer.ProducerImplB",
					"consumer.ConsumerImplB", null);
			assertThat(compareScopes(wire.getScope(), new String[] {"A", "B", "C"})).as("Test Wire Scope without WirePermission: NOT OK.").isTrue();
			BasicEnvelope envelope = new BasicEnvelope(Integer.valueOf(42),
					"window2", "YY");
			wire.update(envelope);
			assertThat(wire
					.getLastValue()).as("Test Wire update without permission: NOT OK.").isNull();
			wa.deleteWire(wire);
			setWirePermissions(new String[] {"B", "C"}, consumer, true);
			setWirePermissions(new String[] {"A", "B"}, producer, false);
			wire = helper.createWire(wa, "producer.ProducerImplB",
					"consumer.ConsumerImplB", null);
			assertThat(compareScopes(wire.getScope(), new String[] {"B"})).as("Test Wire Scope, after setting WirePermission: NOT OK.").isTrue();
			envelope = new BasicEnvelope(Integer.valueOf(42), "window2", "B");
			wire.update(envelope);
			assertThat(wire.getLastValue()).as("Test Wire update with envelope object: NOT OK.").isEqualTo(envelope);
		}
		finally {
			consumer.uninstall();
			producer.uninstall();
		}
	}

	@Test
	public void testAsteriskConsumer() throws Exception {
		Bundle consumer = installer.installBundle(context.getBundle().getEntry("tb3.jar"), true);
		Bundle producer = installer.installBundle(context.getBundle().getEntry("tb5.jar"), true);
		try {
			Wire wire = helper.createWire(wa, "producer.ProducerImplB",
					"consumer.ConsumerImplC", null);
			assertThat(compareScopes(wire.getScope(), new String[] {"A", "B", "C"})).as("Test Wire Scope without WirePermission: NOT OK.").isTrue();
			BasicEnvelope envelope = new BasicEnvelope("open", "door2", "DD");
			wire.update(envelope);
			assertThat(wire
					.getLastValue()).as("Test Wire update without permission: NOT OK.").isNull();
			setWirePermissions(new String[] {"*"}, consumer, true);
			setWirePermissions(new String[] {"A", "B", "C"}, producer, false);
			wa.deleteWire(wire);
			wire = helper.createWire(wa, "producer.ProducerImplB",
					"consumer.ConsumerImplC", null);
			assertThat(compareScopes(wire.getScope(), new String[] {"A", "B", "C"})).as("Test Wire Scope, after setting WirePermission: NOT OK.").isTrue();
			envelope = new BasicEnvelope("locked", "backdoor", "B");
			wire.update(envelope);
			assertThat(wire.getLastValue()).as("Test Wire update with envelope object: NOT OK.").isEqualTo(envelope);
		}
		finally {
			consumer.uninstall();
			producer.uninstall();
		}
	}

	@Test
	public void testAllAsterisk() throws Exception {
		Bundle consumer = installer.installBundle(context.getBundle().getEntry("tb3.jar"), true);
		Bundle producer = installer.installBundle(context.getBundle().getEntry("tb6.jar"), true);
		try {
			setWirePermissions(new String[] {"*"}, consumer, true);
			setWirePermissions(new String[] {"*"}, producer, false);
			Wire wire = helper.createWire(wa, "producer.ProducerImplC",
					"consumer.ConsumerImplC", null);
			assertThat(compareScopes(wire.getScope(), new String[] {"*"})).as("Test Wire Scope with *(scope) and *(WirePermission): NOT OK.").isTrue();
			BasicEnvelope envelope = new BasicEnvelope(Integer.valueOf(5),
					"numberDoors", "AA");
			wire.update(envelope);
			assertThat(wire.getLastValue()).as("Test Wire update with envelope object: NOT OK.").isEqualTo(envelope);
		}
		finally {
			consumer.uninstall();
			producer.uninstall();
		}
	}

	@Test
	public void testMissingScopePermissions() throws Exception {
		Bundle consumer = installer.installBundle(context.getBundle().getEntry("tb2.jar"), true);
		Bundle producer = installer.installBundle(context.getBundle().getEntry("tb5.jar"), true);
		try {
			setWirePermissions(new String[] {"A", "B", "C"}, consumer, true);
			setWirePermissions(new String[] {"X"}, producer, false);
			Wire wire = helper.createWire(wa, "producer.ProducerImplB",
					"consumer.ConsumerImplB", null);
			assertThat(compareScopes(wire
					.getScope(), new String[] {})).as("Test Wire Scope: NOT OK.").isTrue();
			BasicEnvelope envelope = new BasicEnvelope(Integer.valueOf(44),
					"numberWindows", "A");
			wire.update(envelope);
			assertThat(wire.getLastValue()).as("Test Wire update with incompatible wire scope: NOT OK.").isNull();
		}
		finally {
			consumer.uninstall();
			producer.uninstall();
		}
	}

	@Test
	public void testScopeIntersection() throws Exception {
		Bundle consumer = installer.installBundle(context.getBundle().getEntry("tb2.jar"), true);
		Bundle producer = installer.installBundle(context.getBundle().getEntry("tb7.jar"), true);
		try {
			setWirePermissions(new String[] {"*"}, consumer, true);
			setWirePermissions(new String[] {"C", "E"}, producer, false);
			Wire wire = helper.createWire(wa, "producer.ProducerImplD",
					"consumer.ConsumerImplB", null);
			assertThat(compareScopes(wire
					.getScope(), new String[] {"C"})).as("Test Wire Scope: NOT OK.").isTrue();
			BasicEnvelope envelope = new BasicEnvelope("unlocked",
					"front.Door", "A");
			wire.update(envelope);
			assertThat(wire.getLastValue()).as("Test Wire update with incompatible wire scope: NOT OK.").isNull();
			envelope = new BasicEnvelope("closed", "front.Door", "C");
			wire.update(envelope);
			assertThat(wire.getLastValue()).as("Test Wire update with correct envelope object: NOT OK.").isEqualTo(envelope);
		}
		finally {
			consumer.uninstall();
			producer.uninstall();
		}
	}

	// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// ///////////////////////////////////////state manager test methods end
	// here //////////////////////////////////////////
	// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// /////////////////////////////////////////////////// auxil methods
	// ///////////////////////////////////////////////////
	// /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	/*
	 * flag - true -> consumer, false -> producer
	 */
	private void setWirePermissions(String[] permissions, Bundle bundle,
			boolean flag) {
		String type = flag ? WirePermission.CONSUME : WirePermission.PRODUCE;
		PermissionInfo[] perms = new PermissionInfo[permissions.length];
		for (int i = 0; i < permissions.length; i++) {
			perms[i] = new PermissionInfo(WirePermission.class.getName(),
					permissions[i], type);
		}
		pa.setPermissions(bundle.getLocation(), perms);
		synchronized (permBundles) {
			permBundles.add(bundle.getLocation());
		}
	}

	private void removePermissions() {
		synchronized (permBundles) {
			for (Iterator<String> i = permBundles.iterator(); i.hasNext();) {
				pa.setPermissions(i.next(), null);
			}
			permBundles.clear();
		}
	}

	private boolean compareScopes(String[] current, String[] correct) {
		boolean flag = false;
		if (current == null && correct == null)
			return true;
		if (current == null || correct == null)
			return false;
		if (current.length != correct.length) {
			return false;
		}
		else {
			for (int counter = 0; counter < correct.length; counter++) {
				for (int counter1 = 0; counter1 < current.length; counter1++) {
					if (current[counter1].equals(correct[counter])) {
						flag = true;
						break;
					}
				}
				if (!flag)
					return false;
			}
		}
		return true;
	}

	private Wire[] getConnected(Wire[] all) {
		if (all == null)
			return null;
		List<Wire> v = new ArrayList<>();
		for (int counter = 0; counter < all.length; counter++) {
			if (all[counter].isConnected())
				v.add(all[counter]);
		}
		if (v.size() == 0)
			return null;
		Wire[] toReturn = new Wire[v.size()];
		v.toArray(toReturn);
		return toReturn;
	}

	private void compareConnected(String producerPID, String consumerPID) {
		Wire[] wires;
		Wire[] returned;
		if (!producerPID.equals("")) {
			wires = getConnected(getWiresForFilter("(wireadmin.producer.pid="
					+ producerPID + ")"));
			returned = returnedWires.remove(producerPID);
			assertThat(returned).as("{" + producerPID
					+ "} Invokes consumersConnected(..): NOT OK, Not invoked").isNotNull();
			if (returned.length == 0) {
				returned = null;
			}
			assertThat(compareWires_Flavors(wires, returned)).as("{" + producerPID
					+ "} Invokes consumersConnected(..): NOT OK, Wrong Wire[]").isTrue();
		}
		if (!consumerPID.equals("")) {
			wires = getConnected(getWiresForFilter("(wireadmin.consumer.pid="
					+ consumerPID + ")"));
			returned = returnedWires.remove(consumerPID);
			assertThat(returned).as("{" + consumerPID
					+ "} Invokes producersConnected(..): NOT OK, Not invoked").isNotNull();
			if (returned.length == 0) {
				returned = null;
			}
			assertThat(compareWires_Flavors(wires, returned)).as("{" + consumerPID
					+ "} Invokes producersConnected(..): NOT OK, Wrong Wire[]").isTrue();
		}
	}

	void addInHashtable(String pid, Wire[] wires) {
		returnedWires.put(pid, (wires == null) ? new Wire[0] : wires);
	}

	private boolean compareWires_Flavors(Object[] current, Object[] correct) {
		boolean flag = false;
		if (current == null && correct == null)
			return true;
		if (current == null || correct == null)
			return false;
		if (current.length != correct.length)
			return false;
		for (int counter = 0; counter < correct.length; counter++) {
			for (int counter1 = 0; counter1 < current.length; counter1++) {
				if (current[counter1].equals(correct[counter])) {
					flag = true;
					break;
				}
			}
			if (!flag)
				return false;
		}
		return true;
	}

	private boolean compareWireProperties(Dictionary<String,Object> current,
			Dictionary<String,Object> correct) {
		Enumeration<String> keys = correct.keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			if (!(correct.get(key).equals(current.get(key)))) {
				return false;
			}
		}
		return true;
	}

	private boolean notUpdated(Wire wire) {
		wire.update("TEST FOR WIRE UPDATE");
		return wire.getLastValue() == null;
	}

	private Wire[] getWiresForFilter(String filter) {
		try {
			return wa.getWires(filter);
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// //////// some helper methods for additonal debugging ... not used by
	// default
	@SuppressWarnings("unused")
	private void dumpWires(String filter) {
		synchronized (this.getClass()) { // to avoid messups when called from
			// different threads
			try {
				Wire[] wiress = wa.getWires(filter);
				if (wiress == null) {
					System.out.println("No wires registered for filter "
							+ filter);
					return;
				}
				for (int i = 0; i < wiress.length; i++) {
					System.out.println(wiress[i]);
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unused")
	private void dumpProducers(String filter) {
		synchronized (this.getClass()) { // to avoid messups when called from
			// different threads
			try {
				ServiceReference< ? >[] producers = context
						.getServiceReferences(Producer.class.getName(), filter);
				if (producers == null) {
					System.out.println("No producers registered for filter "
							+ filter);
					return;
				}
				for (int i = 0; i < producers.length; i++) {
					String[] props = producers[i].getPropertyKeys();
					System.out.println("Producer: "
							+ context.getService(producers[i]));
					context.ungetService(producers[i]);
					System.out.println("Producer props:");
					for (int j = 0; j < props.length; j++) {
						System.out.println("property: " + props[j]);
						System.out.println("value: "
								+ producers[i].getProperty(props[j]));
					}
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unused")
	private void dumpConsumers(String filter) {
		synchronized (this.getClass()) { // to avoid messups when called from
			// different threads
			try {
				ServiceReference< ? >[] consumers = context
						.getServiceReferences(Consumer.class.getName(), filter);
				if (consumers == null) {
					System.out.println("No consumer registered for filter "
							+ filter);
					return;
				}
				for (int i = 0; i < consumers.length; i++) {
					String[] props = consumers[i].getPropertyKeys();
					System.out.println("Consumer: "
							+ context.getService(consumers[i]));
					context.ungetService(consumers[i]);
					System.out.println("Consumer props:");
					for (int j = 0; j < props.length; j++) {
						System.out.println("property: " + props[j]);
						System.out.println("value: "
								+ consumers[i].getProperty(props[j]));
					}
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
