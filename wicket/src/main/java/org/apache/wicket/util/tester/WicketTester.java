/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.wicket.util.tester;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import org.apache.wicket.Component;
import org.apache.wicket.MarkupContainer;
import org.apache.wicket.Page;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.ng.mock.MockApplication;
import org.apache.wicket.util.diff.DiffUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A helper class to ease unit testing of Wicket applications without the need for a servlet
 * container. To start a test, either use <code>startPage</code> or <code>startPanel</code>:
 * 
 * <pre>
 * // production page
 * public class MyPage extends WebPage
 * {
 * 	public MyPage()
 * 	{
 * 		add(new Label(&quot;myMessage&quot;, &quot;Hello!&quot;));
 * 		add(new Link(&quot;toYourPage&quot;)
 * 		{
 * 			public void onClick()
 * 			{
 * 				setResponsePage(new YourPage(&quot;Hi!&quot;));
 * 			}
 * 		});
 * 	}
 * }
 * </pre>
 * 
 * <pre>
 * // test code
 * private WicketTester tester;
 * 
 * public void setUp()
 * {
 * 	tester = new WicketTester();
 * }
 * 
 * public void testRenderMyPage()
 * {
 * 	//start and render the test page
 * 	tester.startPage(MyPage.class);
 * 	//assert rendered page class
 * 	tester.assertRenderedPage(MyPage.class);
 * 	//assert rendered label component
 * 	tester.assertLabel(&quot;myMessage&quot;, &quot;Hello!&quot;);
 * }
 * </pre>
 * 
 * The above example is straight forward: start <code>MyPage.class</code> and assert
 * <code>Label</code> it rendered. Next, we try to navigate through a <code>Link</code>:
 * 
 * <pre>
 * // production page
 * public class YourPage extends WebPage
 * {
 * 	public YourPage(String message)
 * 	{
 * 		add(new Label(&quot;yourMessage&quot;, message));
 * 		info(&quot;Wicket Rocks ;-)&quot;);
 * 	}
 * }
 * 
 * //test code
 * public void testLinkToYourPage()
 * {
 * 	tester.startPage(MyPage.class);
 * 	//click link and render
 * 	tester.clickLink(&quot;toYourPage&quot;);
 * 	tester.assertRenderedPage(YourPage.class);
 * 	tester.assertLabel(&quot;yourMessage&quot;, &quot;Hi!&quot;);
 * }
 * </pre>
 * 
 * <code>tester.clickLink(path);</code> will simulate user click on the component (in this case,
 * it's a <code>Link</code>) and render the response page <code>YourPage</code>. Ok, unit test of
 * <code>MyPage</code> is completed. Now we test <code>YourPage</code> standalone:
 * 
 * <pre>
 * //test code
 * public void testRenderYourPage()
 * {
 * 	// provide page instance source for WicketTester
 * 	tester.startPage(new TestPageSource()
 * 	{
 * 		public Page getTestPage()
 * 		{
 * 			return new YourPage(&quot;mock message&quot;);
 * 		}
 * 	});
 * 	tester.assertRenderedPage(YourPage.class);
 * 	tester.assertLabel(&quot;yourMessage&quot;, &quot;mock message&quot;);
 * 	// assert feedback messages in INFO Level
 * 	tester.assertInfoMessages(new String[] { &quot;Wicket Rocks ;-)&quot; });
 * }
 * </pre>
 * 
 * Instead of <code>tester.startPage(pageClass)</code>, we define a
 * {@link org.apache.wicket.util.tester.ITestPageSource} to provide testing page instance for
 * <code>WicketTester</code>. This is necessary because <code>YourPage</code> uses a custom
 * constructor, which is very common for transferring model data, but cannot be instantiated by
 * reflection. Finally, we use <code>assertInfoMessages</code> to assert there is a feedback message
 * "Wicket Rocks ;-)" at the INFO level.
 * 
 * Many methods require a 'path' parameter. E.g. the page relative path can be obtained via
 * {@link Component#getPageRelativePath()}. Since each Component has an ID/name, any Component can
 * also be referenced by its ID {@link MarkupContainer#get(String)}. And since MarkupContainer's and
 * its subclasses are containers which allow to add Components (in sync with the markup hierarchy),
 * you may not only access direct childs but also subchilds like get("myPanel:myForm:myNameField")
 * separating each ID with a ':'.
 * 
 * TODO General: Example usage of FormTester
 * 
 * @author Ingram Chen
 * @author Juergen Donnerstag
 * @author Frank Bille
 * @since 1.2.6
 */
public class WicketTester extends BaseWicketTester
{


	/** log. */
	private static final Logger log = LoggerFactory.getLogger(WicketTester.class);

	/**
	 * Creates a <code>WicketTester</code> and automatically creates a <code>WebApplication</code>,
	 * but the tester will have no home page.
	 */
	public WicketTester()
	{
	}

	/**
	 * Creates a <code>WicketTester</code> and automatically creates a <code>WebApplication</code>.
	 * 
	 * @param homePage
	 *            a home page <code>Class</code>
	 */
	public WicketTester(final Class<? extends Page> homePage)
	{
		super(homePage);
	}

	/**
	 * Creates a <code>WicketTester</code>.
	 * 
	 * @param application
	 *            a <code>WicketTester</code> <code>WebApplication</code> object
	 */
	public WicketTester(final MockApplication application)
	{
		super(application);
	}

	/**
	 * Creates a <code>WicketTester</code> to help unit testing.
	 * 
	 * @param application
	 *            a <code>WicketTester</code> <code>WebApplication</code> object
	 * @param path
	 *            the absolute path on disk to the web application's contents (e.g. war root) - may
	 *            be <code>null</code>
	 * 
	 * @see org.apache.wicket.protocol.http.MockWebApplication#MockWebApplication(org.apache.wicket.protocol.http.WebApplication,
	 *      String)
	 */
// public WicketTester(final WebApplication application, final String path)
// {
// super(application, path);
// }


	/**
	 * Asserts that the Ajax location header is present.
	 */
	public void assertAjaxLocation()
	{
		if (null != getLastResponse().getHeader("Location"))
		{
			throw new AssertionFailedError(
				"Location header should *not* be present when using Ajax");
		}

		String ajaxLocation = getLastResponse().getHeader("Ajax-Location");
		if (null == ajaxLocation)
		{
			throw new AssertionFailedError("Ajax-Location header should be present when using Ajax");
		}

		int statusCode = getLastResponse().getStatus();
		if (statusCode != 200)
		{
			throw new AssertionFailedError("Expected HTTP status code to be 200 (OK)");
		}
	}

	/**
	 * Asserts a <code>Component</code> class.
	 * 
	 * @param path
	 *            path to <code>Component</code>
	 * @param expectedComponentClass
	 *            expected <code>Component</code> class
	 */
	public void assertComponent(String path, Class<? extends Component> expectedComponentClass)
	{
		assertResult(isComponent(path, expectedComponentClass));
	}

	/**
	 * Tests that a <code>Component</code> has been added to a <code>AjaxRequestTarget</code>, using
	 * {@link AjaxRequestTarget#addComponent(Component)}. This method actually tests that a
	 * <code>Component</code> is on the Ajax response sent back to the client.
	 * <p>
	 * PLEASE NOTE! This method doesn't actually insert the <code>Component</code> in the client DOM
	 * tree, using Javascript. But it shouldn't be needed because you just have to trust that Wicket
	 * Ajax Javascript works.
	 * 
	 * @param component
	 *            a <code>Component</code> to be tested
	 */
	public void assertComponentOnAjaxResponse(Component component)
	{
		Result result = isComponentOnAjaxResponse(component);
		assertResult(result);
	}

	/**
	 * Tests that a <code>Component</code> has been added to a <code>AjaxRequestTarget</code>, using
	 * {@link AjaxRequestTarget#addComponent(Component)}. This method actually tests that a
	 * <code>Component</code> is on the Ajax response sent back to the client.
	 * <p>
	 * PLEASE NOTE! This method doesn't actually insert the <code>Component</code> in the client DOM
	 * tree, using Javascript. But it shouldn't be needed because you just have to trust that Wicket
	 * Ajax Javascript works.
	 * 
	 * @param componentPath
	 *            a <code>Component</code> path to test
	 */
	public void assertComponentOnAjaxResponse(String componentPath)
	{
		assertComponentOnAjaxResponse(getComponentFromLastRenderedPage(componentPath));
	}

	/**
	 * Asserts the content of last rendered page contains (matches) a given regex pattern.
	 * 
	 * @param pattern
	 *            a reqex pattern to match
	 */
	public void assertContains(String pattern)
	{
		assertResult(ifContains(pattern));
	}

	/**
	 * Asserts error-level feedback messages.
	 * 
	 * @param expectedErrorMessages
	 *            expected error messages
	 */
	public void assertErrorMessages(String[] expectedErrorMessages)
	{
		List<Serializable> actualMessages = getMessages(FeedbackMessage.ERROR);
		List<Serializable> msgs = new ArrayList<Serializable>();
		for (Iterator<Serializable> iterator = actualMessages.iterator(); iterator.hasNext();)
		{
			msgs.add(iterator.next().toString());
		}
		WicketTesterHelper.assertEquals(Arrays.asList(expectedErrorMessages), msgs);
	}

	/**
	 * Assert info-level feedback messages.
	 * 
	 * @param expectedInfoMessages
	 *            expected info messages
	 */
	public void assertInfoMessages(String[] expectedInfoMessages)
	{
		List<Serializable> actualMessages = getMessages(FeedbackMessage.INFO);
		WicketTesterHelper.assertEquals(Arrays.asList(expectedInfoMessages), actualMessages);
	}

	/**
	 * Asserts that a <code>Component</code> is invisible.
	 * 
	 * @param path
	 *            path to <code>Component</code>
	 */
	public void assertInvisible(String path)
	{
		assertResult(isInvisible(path));
	}

	/**
	 * Asserts the text of a <code>Label</code> <code>Component</code>.
	 * 
	 * @param path
	 *            path to <code>Label</code> <code>Component</code>
	 * @param expectedLabelText
	 *            expected text of the <code>Label</code>
	 */
	public void assertLabel(String path, String expectedLabelText)
	{
		Label label = (Label)getComponentFromLastRenderedPage(path);
		Assert.assertEquals(expectedLabelText, label.getDefaultModelObjectAsString());
	}

	/**
	 * Asserts the model value of a component.
	 * 
	 * @param path
	 *            path to the component on the page
	 * @param expectedValue
	 *            expected value of the component's model
	 */
	public void assertModelValue(String path, Object expectedValue)
	{
		Component component = getComponentFromLastRenderedPage(path);
		Assert.assertEquals(expectedValue, component.getDefaultModelObject());
	}

	/**
	 * Asserts the model of a {@link ListView}.
	 * 
	 * @param path
	 *            path to a {@link ListView} <code>Component</code>
	 * @param expectedList
	 *            expected <code>List</code> in the model of the given {@link ListView}
	 */
	@Override
	public void assertListView(String path, List<?> expectedList)
	{
		ListView<?> listView = (ListView<?>)getComponentFromLastRenderedPage(path);
		WicketTesterHelper.assertEquals(expectedList, listView.getList());
	}

	/**
	 * Asserts no error-level feedback messages.
	 */
	public void assertNoErrorMessage()
	{
		List<Serializable> messages = getMessages(FeedbackMessage.ERROR);
		Assert.assertTrue("expect no error message, but contains\n" +
			WicketTesterHelper.asLined(messages), messages.isEmpty());
	}

	/**
	 * Asserts no info-level feedback messages.
	 */
	public void assertNoInfoMessage()
	{
		List<Serializable> messages = getMessages(FeedbackMessage.INFO);
		Assert.assertTrue("expect no info message, but contains\n" +
			WicketTesterHelper.asLined(messages), messages.isEmpty());
	}

	/**
	 * Asserts a last-rendered <code>Page</code> class.
	 * 
	 * @param expectedRenderedPageClass
	 *            expected class of last rendered <code>Page</code>
	 */
	public void assertRenderedPage(Class<? extends Page> expectedRenderedPageClass)
	{
		assertResult(isRenderedPage(expectedRenderedPageClass));
	}

	/**
	 * Asserts last-rendered <code>Page</code> against an expected HTML document.
	 * <p>
	 * Use <code>-Dwicket.replace.expected.results=true</code> to automatically replace the expected
	 * output file.
	 * 
	 * @param clazz
	 *            <code>Class</code> used to load the file (relative to <code>clazz</code> package)
	 * @param filename
	 *            expected output filename <code>String</code>
	 * @throws Exception
	 */
	@Override
	public void assertResultPage(final Class<?> clazz, final String filename) throws Exception
	{
		String document = getLastResponseAsString();
		DiffUtil.validatePage(document, clazz, filename, true);
	}

	/**
	 * Asserts last-rendered <code>Page</code> against an expected HTML document as a
	 * <code>String</code>
	 * 
	 * @param expectedDocument
	 *            expected output <code>String</code>
	 * @throws Exception
	 */
	public void assertResultPage(final String expectedDocument) throws Exception
	{
		// Validate the document
		String document = getLastResponseAsString();
		Assert.assertEquals(expectedDocument, document);
	}

	/**
	 * Asserts that a <code>Component</code> is visible.
	 * 
	 * @param path
	 *            path to a <code>Component</code>
	 */
	public void assertVisible(String path)
	{
		assertResult(isVisible(path));
	}

	private void assertResult(Result result)
	{
		if (result.wasFailed())
		{
			throw new AssertionFailedError(result.getMessage());
		}
	}

	public void clickLink(Component link)
	{
		clickLink(link.getPageRelativePath());
	}


}