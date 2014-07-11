/*
 * Copyright 2011 Thomas Bocek
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.tomp2p.examples;

import java.net.InetAddress;
import java.util.Random;

import net.tomp2p.connection.Bindings;
import net.tomp2p.futures.BaseFuture;
import net.tomp2p.futures.FutureDiscover;
import net.tomp2p.nat.FutureRelayNAT;
import net.tomp2p.nat.FutureNAT;
import net.tomp2p.nat.PeerNAT;
import net.tomp2p.p2p.Peer;
import net.tomp2p.p2p.PeerBuilder;
import net.tomp2p.peers.Number160;
import net.tomp2p.peers.PeerAddress;

public class ExampleNAT {
	public void startServer() throws Exception {
		Peer peer = null;
		try {
			Random r = new Random(42L);
			// peer.getP2PConfiguration().setBehindFirewall(true);
			Bindings b = new Bindings().addInterface("eth0");
			peer = new PeerBuilder(new Number160(r)).bindings(b).ports(4000).start();
			System.out.println("peer started.");
			for (;;) {
				for (PeerAddress pa : peer.peerBean().peerMap().all()) {
					BaseFuture future = peer.ping().peerAddress(pa).tcpPing().start();
					if (future.isSuccess()) {
						System.out.println("peer online (TCP):" + pa);
					}
					else {
						System.out.println("offline " + pa);
					}
					future = peer.ping().peerAddress(pa).start();
					if (future.isSuccess()) {
						System.out.println("peer online (UDP):" + pa);
					}
					else {
						System.out.println("offline " + pa);
					}
				}
				Thread.sleep(1500);
			}
		} finally {
			peer.shutdown();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length > 0) {
			startClientNAT(args[0]);
		} else {
			ExampleNAT t = new ExampleNAT();
			t.startServer();
		}
	}

	public static void startClientNAT(String ip) throws Exception {
		Random r = new Random(43L);
		Peer peer = new PeerBuilder(new Number160(r)).ports(4000).behindFirewall().start();
		PeerNAT peerNAT = new PeerNAT(peer);
		PeerAddress pa = new PeerAddress(Number160.ZERO, InetAddress.getByName(ip), 4000, 4000);
		
		FutureDiscover fd = peer.discover().peerAddress(pa).start();
		FutureNAT fn = peerNAT.startSetupPortforwarding(fd);
		FutureRelayNAT frn = peerNAT.bootstrapBuilder(fd.reporter()).startRelay(fn);
		
		fd.awaitUninterruptibly();
		if (fd.isSuccess()) {
			System.out.println("found that my outside address is " + fd.peerAddress());
		} else {
			System.out.println("failed " + fd.failedReason());
		}
		
		frn.awaitUninterruptibly();
		
		peer.shutdown();
	}
}
