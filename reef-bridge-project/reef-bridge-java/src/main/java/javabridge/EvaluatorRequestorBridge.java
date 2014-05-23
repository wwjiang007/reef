/**
 * Copyright (C) 2013 Microsoft Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package javabridge;

import com.microsoft.reef.driver.catalog.RackDescriptor;
import com.microsoft.reef.driver.catalog.ResourceCatalog;
import com.microsoft.reef.driver.evaluator.EvaluatorRequest;
import com.microsoft.reef.driver.evaluator.EvaluatorRequestor;
import com.microsoft.reef.proto.DriverRuntimeProtocol;
import com.microsoft.reef.runtime.common.driver.catalog.RackDescriptorImpl;
import com.microsoft.reef.runtime.common.driver.catalog.ResourceCatalogImpl;
import com.microsoft.tang.Injector;
import com.microsoft.tang.JavaConfigurationBuilder;
import com.microsoft.tang.Tang;
import com.microsoft.tang.exceptions.InjectionException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class EvaluatorRequestorBridge extends NativeBridge {
    private static final Logger LOG = Logger.getLogger(EvaluatorRequestorBridge.class.getName());

    // accumulate how many evaluators have been submitted through this instance
    // of EvaluatorRequestorBridge
    private int clrEvaluatorsNumber;

    private EvaluatorRequestor jevaluatorRequestor;

    public EvaluatorRequestorBridge(EvaluatorRequestor evaluatorRequestor)
    {
        jevaluatorRequestor = evaluatorRequestor;
        clrEvaluatorsNumber = 0;
    }

    public void submit( final int evaluatorsNumber, final int memory,  String rack)
    {
        clrEvaluatorsNumber += evaluatorsNumber;
        final JavaConfigurationBuilder cb = Tang.Factory.getTang().newConfigurationBuilder();
        cb.bindImplementation(ResourceCatalog.class, ResourceCatalogImpl.class);
        Injector injector = Tang.Factory.getTang().newInjector(cb.build());
        ResourceCatalog resourceCatalog;
        try {
          resourceCatalog = injector.getInstance(ResourceCatalog.class);
        } catch (final InjectionException e) {
          LOG.log(Level.SEVERE, "Cannot inject resource catalog", e);
          throw new RuntimeException("Cannot inject resource catalog");
        }

        ResourceCatalogImpl catalog;
        try {
          catalog = (ResourceCatalogImpl) resourceCatalog;
        } catch (final ClassCastException e) {
          LOG.log(Level.SEVERE, "Failed to cast to ResourceCatalogImpl.", e);
          throw e;
        }
        if(rack == null || rack.isEmpty())
        {
          rack = "/default-rack";
        }
        catalog.handle(DriverRuntimeProtocol.NodeDescriptorProto.newBuilder()
                .setRackName(rack)
                .setHostName("HostName")
                .setPort(0)
                .setMemorySize(memory)
                .setIdentifier("clrBridgeRackCatalog")
                .build());
         RackDescriptor rackDescriptor = (RackDescriptor)(catalog.getRacks().toArray())[0];
         EvaluatorRequest request = EvaluatorRequest.newBuilder().fromDescriptor(rackDescriptor)
                  .setNumber(evaluatorsNumber)
                  .setMemory(memory)
                  .build();

        LOG.log(Level.INFO, String.format("submitting %s evaluator to rack %s", evaluatorsNumber, rack) );
        jevaluatorRequestor.submit(request);
    }

    public int getEvaluatorNumber() {
        return clrEvaluatorsNumber;
    }

    @Override
    public void close()
    {
    }
}