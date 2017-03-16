/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
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
 * Contributors:
 *     Thierry Delprat
 */
package org.nuxeo.segment.io.test;

import java.io.Serializable;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.UnboundEventContext;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.segment.io.SegmentIO;
import org.nuxeo.segment.io.SegmentIOComponent;
import org.nuxeo.segment.io.SegmentIOMapper;
import org.nuxeo.segment.io.SegmentIOUserFilter;

import com.google.inject.Inject;

@Deploy({ "org.nuxeo.segmentio.connector"})
@RunWith(FeaturesRunner.class)
@LocalDeploy("org.nuxeo.segmentio.connector:segmentio-contribs.xml")
@Features(CoreFeature.class)
public class TestSegmentIOService {

    @Inject
    EventProducer eventProducer;

    @Inject
    CoreSession session;

    @Inject
    EventService eventService;

    protected void waitForAsyncCompletion() {
        TransactionHelper.commitOrRollbackTransaction();
        eventService.waitForAsyncCompletion();
        TransactionHelper.startTransaction();
    }

    protected void sendAuthenticationEvent(Principal principal) throws Exception {
            Map<String, Serializable> props = new HashMap<String, Serializable>();
            props.put("AuthenticationPlugin", "FakeAuth");
            props.put("LoginPlugin", "FakeLogin");
            EventContext ctx = new UnboundEventContext(principal, props);
            eventProducer.fireEvent(ctx.newEvent("loginSuccess"));
    }


    @Test
    public void checkDeploy() {

        SegmentIOComponent component = (SegmentIOComponent) Framework.getRuntime().getComponent(SegmentIOComponent.class.getName());
        Assert.assertNotNull(component);

        SegmentIO service = Framework.getLocalService(SegmentIO.class);
        Assert.assertNotNull(service);

        Map<String, List<SegmentIOMapper>> allMappers = component.getAllMappers();
        Assert.assertTrue(allMappers.size()>0);
        Assert.assertTrue(allMappers.containsKey("loginSuccess"));
        Assert.assertTrue(allMappers.get("loginSuccess").size()>0);
        Assert.assertTrue(allMappers.get("loginSuccess").get(0).getName().equals("testIdentify"));

    }

    @Test
    public void shouldRunIdentifyOnLogin() throws Exception {

        SegmentIOComponent component = (SegmentIOComponent) Framework.getRuntime().getComponent(SegmentIOComponent.class.getName());
        Assert.assertNotNull(component);

        component.getTestData().clear();

        sendAuthenticationEvent(session.getPrincipal());

        waitForAsyncCompletion();

        List<Map<String, Object>> testData  = component.getTestData();

        Assert.assertTrue(testData.size()>0);

        Map<String, Object> data = testData.remove(0);
        Assert.assertEquals("identify", data.get("action"));
        Assert.assertEquals("FakeAuth", data.get("plugin"));

        Map<String, Object> grpdata = testData.remove(0);
        Assert.assertEquals("group", grpdata.get("action"));
        Assert.assertEquals("TestGroup", grpdata.get("name"));

    }

    @Test
    public void shouldRunTracOnDocEvent() throws Exception {

        SegmentIOComponent component = (SegmentIOComponent) Framework.getRuntime().getComponent(SegmentIOComponent.class.getName());
        Assert.assertNotNull(component);

        component.getTestData().clear();

        DocumentModel doc = session.createDocumentModel("/", "testDoc","File");
        doc.setPropertyValue("dc:title", "Test Doc");
        doc = session.createDocument(doc);
        session.save();

        waitForAsyncCompletion();

        List<Map<String, Object>> testData  = component.getTestData();

        Assert.assertTrue(testData.size()>0);

        Map<String, Object> data = testData.remove(0);

        //System.out.println(data);
        Assert.assertEquals("track", data.get("action"));
        Assert.assertEquals("Test Doc", data.get("title"));
    }



    @Test
    public void shouldHaveDefaultProvidersConfig() throws Exception {

        SegmentIO sio = Framework.getLocalService(SegmentIO.class);
        Assert.assertNotNull(sio);

        Map<String, Boolean> integrations = sio.getIntegrations();

        Assert.assertNotNull(integrations);

        Assert.assertTrue(integrations.containsKey("Marketo"));
        Assert.assertTrue(integrations.get("Marketo"));

    }

    @Test
    public void shouldHaveUserFilter() throws Exception {

        SegmentIO sio = Framework.getLocalService(SegmentIO.class);
        Assert.assertNotNull(sio);

        SegmentIOComponent component = (SegmentIOComponent) Framework.getRuntime().getComponent(SegmentIOComponent.class.getName());
        Assert.assertNotNull(component);

        SegmentIOUserFilter filters = component.getUserFilters();

        Assert.assertNotNull(filters);

        Assert.assertFalse(filters.isEnableAnonymous());

        Assert.assertTrue(filters.getBlackListedUsers().contains("RemoteConnectInstance"));

        Assert.assertTrue(filters.canTrack(session.getPrincipal().getName()));

    }

}
