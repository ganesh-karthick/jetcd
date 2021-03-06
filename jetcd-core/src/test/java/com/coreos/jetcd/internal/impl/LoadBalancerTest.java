/**
 * Copyright 2017 The jetcd authors
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
package com.coreos.jetcd.internal.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreos.jetcd.Client;
import com.coreos.jetcd.KV;
import com.coreos.jetcd.data.Response;
import com.coreos.jetcd.internal.infrastructure.EtcdClusterResource;
import com.coreos.jetcd.kv.PutResponse;
import io.grpc.PickFirstBalancerFactory;
import io.grpc.util.RoundRobinLoadBalancerFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.List;
import java.util.stream.Collectors;

/**
 * KV service test cases.
 */
public class LoadBalancerTest {
  @Rule
  public final EtcdClusterResource clusterResource = new EtcdClusterResource("load-balancer-etcd", 3);
  @Rule
  public Timeout timeout = Timeout.seconds(10);

  @Test
  public void testPickFirstBalancerFactory() throws Exception {
    final List<String> endpoints = clusterResource.cluster().getClientEndpoints();

    try (Client client = Client.builder()
            .endpoints(endpoints)
            .loadBalancerFactory(PickFirstBalancerFactory.getInstance())
            .build();

         KV kv = client.getKVClient()) {

      long lastMemberId = 0;

      for (int i = 0; i < endpoints.stream().collect(Collectors.joining(",")).length() * 2; i++) {
        Response response = kv.put(TestUtil.randomByteSequence(), TestUtil.randomByteSequence()).get();

        if (i == 0) {
          lastMemberId = response.getHeader().getMemberId();
        }

        assertThat(response.getHeader().getMemberId()).isEqualTo(lastMemberId);
      }
    }
  }

  @Test
  public void testRoundRobinLoadBalancerFactory() throws Exception {
    final List<String> endpoints = clusterResource.cluster().getClientEndpoints();

    try (Client client = Client.builder()
            .endpoints(endpoints)
            .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
            .build();
         KV kv = client.getKVClient()) {

      long lastMemberId = 0;
      long differences = 0;

      for (int i = 0; i < endpoints.stream().collect(Collectors.joining(",")).length(); i++) {
        PutResponse response = kv.put(TestUtil.randomByteSequence(), TestUtil.randomByteSequence()).get();

        if (i > 0 && lastMemberId != response.getHeader().getMemberId()) {
          differences++;
        }

        lastMemberId = response.getHeader().getMemberId();
      }

      assertThat(differences).isNotEqualTo(lastMemberId);
    }
  }
}
