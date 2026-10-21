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

import org.osgi.service.wireadmin.Producer;
import org.osgi.service.wireadmin.Wire;

/**
 * A simple producer for test purposes
 * 
 * @author Neviana Ducheva
 */
public class ProducerImpl implements Producer {
	private final WireAdminControl	wac;
	private final String			pid;

	public ProducerImpl(WireAdminControl wac, String pid) {
		this.wac = wac;
		this.pid = pid;
	}

	@Override
	public void consumersConnected(Wire[] wires) {
		if (wac.getProperty("dump.now") != null) {
			System.out.println("**********************************************************************");
			System.out.println("consumersConnected called and will set counter to "
							+
			    		  (wac.synchCounterx + 1));
			System.out.println("producer is: " + pid + " " + hashCode());
		
			      if (wires != null) {
			        for (int i = 0; i < wires.length; i++) {
			          Wire wire = wires[i];
					System.out.println("wire is: " + wire);
					System.out.println("connected: " + wire.isConnected());
					System.out.println("properties: " + wire.getProperties());
			        }
			      } else {
				System.out.println("wires are null");
			      }
			System.out.println("**********************************************************************");
//		          new Exception("Stack trace").printStackTrace();
            }
		wac.addInHashtable(pid, wires);
		wac.syncup(pid+ " " + wires); 
	}

	@Override
	public Object polled(Wire wire) {
		return "";
	}
}
