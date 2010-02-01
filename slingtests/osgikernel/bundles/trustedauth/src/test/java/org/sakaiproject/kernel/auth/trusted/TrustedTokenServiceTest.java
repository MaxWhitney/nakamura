/*
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.kernel.auth.trusted;

import org.apache.commons.lang.StringUtils;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.kernel.auth.trusted.TrustedTokenServiceImpl.TrustedUser;

import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;


/**
 *
 */
public class TrustedTokenServiceTest  {

  private TrustedTokenServiceImpl trustedTokenService;
  private List<Object> mocks = new ArrayList<Object>();

  @Before
  public void before() throws NoSuchAlgorithmException {
    mocks.clear();
    trustedTokenService = new TrustedTokenServiceImpl();
  }
  
  public ComponentContext configureForSession() {
    ComponentContext context = createMock(ComponentContext.class);
    Hashtable<String, Object> dict = new Hashtable<String, Object>();
    dict.put(TrustedTokenServiceImpl.USE_SESSION, true);
    dict.put(TrustedTokenServiceImpl.COOKIE_NAME, "secure-cookie");
    dict.put(TrustedTokenServiceImpl.TTL, 1200000L);
    dict.put(TrustedTokenServiceImpl.SECURE_COOKIE, false);
    EasyMock.expect(context.getProperties()).andReturn(dict);
    return context;
  }
  
  public ComponentContext configureForCookie() {
    ComponentContext context = createMock(ComponentContext.class);
    Hashtable<String, Object> dict = new Hashtable<String, Object>();
    dict.put(TrustedTokenServiceImpl.USE_SESSION, false);
    dict.put(TrustedTokenServiceImpl.COOKIE_NAME, "secure-cookie");
    dict.put(TrustedTokenServiceImpl.TTL, 1200000L);
    dict.put(TrustedTokenServiceImpl.SECURE_COOKIE, false);
    EasyMock.expect(context.getProperties()).andReturn(dict);
    return context;
  }

  public ComponentContext configureForCookieFast() {
    ComponentContext context = createMock(ComponentContext.class);
    Hashtable<String, Object> dict = new Hashtable<String, Object>();
    dict.put(TrustedTokenServiceImpl.USE_SESSION, false);
    dict.put(TrustedTokenServiceImpl.COOKIE_NAME, "secure-cookie");
    dict.put(TrustedTokenServiceImpl.TTL, 100L);
    dict.put(TrustedTokenServiceImpl.SECURE_COOKIE, false);
    EasyMock.expect(context.getProperties()).andReturn(dict);
    return context;
  }

  @Test
  public void testCookieEncoding() {
    ComponentContext context = configureForCookie();
    replay();
    trustedTokenService.activate(context);        
    
    String cookie = trustedTokenService.encodeCookie("ieb");
    String user = trustedTokenService.decodeCookie(cookie);
    Assert.assertNotNull(user);
    Assert.assertEquals("ieb", user);
    
    long start = System.currentTimeMillis();
    for ( int i = 0; i < 1000;i++ ) {
      cookie = trustedTokenService.encodeCookie("ieb");
    }
    System.err.println("Encode Time "+(System.currentTimeMillis()-start));
    start = System.currentTimeMillis();
    for ( int i = 0; i < 1000;i++ ) {
      user = trustedTokenService.decodeCookie(cookie);
    }
    System.err.println("Decode Time "+(System.currentTimeMillis()-start));
    
    verify();
  }
  
  @Test
  public void testCookieEncodingSafety() {
    ComponentContext context = configureForCookie();
    replay();
    trustedTokenService.activate(context);        
    Assert.assertNull(trustedTokenService.encodeCookie(null));
    Assert.assertNull(trustedTokenService.decodeCookie(null));
    verify();
  }
  
  @Test
  public void testCookieEncodingSafety2() {
    ComponentContext context = configureForCookie();
    replay();
    trustedTokenService.activate(context);        
    String cookie = trustedTokenService.encodeCookie("ieb");
    cookie = cookie+"invalid";
    String user = trustedTokenService.decodeCookie(cookie);
    Assert.assertNull(user);
    verify();
  }
  
  @Test
  public void testCookieEncodingSafety3() {
    ComponentContext context = configureForCookie();
    replay();
    trustedTokenService.activate(context);        
    String cookie = trustedTokenService.encodeCookie("ieb");
    cookie = "a;"+cookie;
    String user = trustedTokenService.decodeCookie(cookie);
    Assert.assertNull(user);
    verify();
  }

  @Test
  public void testCookieEncodingSafety4() {
    ComponentContext context = configureForCookie();
    replay();
    trustedTokenService.activate(context);        
    String cookie = trustedTokenService.encodeCookie("ieb");
    System.err.println("Cookie is "+cookie);
    String[] parts = StringUtils.split(cookie,"@");
    parts[1] = String.valueOf(System.currentTimeMillis()-3600000L);
    cookie = parts[0]+"@"+parts[1]+"@"+parts[2];
    String user = trustedTokenService.decodeCookie(cookie);
    Assert.assertNull(user);
    verify();
  }

  
  @Test
  public void testCookieEncodingTokens() throws InterruptedException {
    ComponentContext context = configureForCookieFast();
    replay();
    trustedTokenService.activate(context);        
    String cookie = trustedTokenService.encodeCookie("ieb");
    Thread.sleep(30L);
    String cookie2 = trustedTokenService.encodeCookie("ieb2");
    String user = trustedTokenService.decodeCookie(cookie);
    Assert.assertNotNull(user);
    Assert.assertEquals("ieb", user);
    user = trustedTokenService.decodeCookie(cookie2);
    Assert.assertNotNull(user);
    Assert.assertEquals("ieb2", user);
    for ( int i = 0; i < 20; i++) {
      Thread.sleep(50L);
      trustedTokenService.encodeCookie("ieb2");
    }
    verify();
  }
  

  @Test
  public void testCookieRefresh() throws InterruptedException {
    ComponentContext context = configureForCookieFast();
    HttpServletResponse response = createMock(HttpServletResponse.class);
    Capture<Cookie> cookieCapture = new Capture<Cookie>();
    response.addCookie(EasyMock.capture(cookieCapture));
    EasyMock.expectLastCall();
    replay();
    trustedTokenService.activate(context);        
    String cookie = trustedTokenService.encodeCookie("ieb");
    Thread.sleep(100L);
    trustedTokenService.refreshToken(response, cookie, "ieb");
    Assert.assertTrue(cookieCapture.hasCaptured());
    Cookie cookie2 = cookieCapture.getValue();
    Assert.assertNotNull(cookie);
    Assert.assertNotSame(cookie, cookie2.getValue());
    Assert.assertEquals("secure-cookie", cookie2.getName());
    String user = trustedTokenService.decodeCookie(cookie2.getValue());
    Assert.assertEquals("ieb", user);
    verify();
  }

  @Test
  public void testCookieNoRefresh() throws InterruptedException {
    ComponentContext context = configureForCookie();
    HttpServletResponse response = createMock(HttpServletResponse.class);
    replay();
    trustedTokenService.activate(context);        
    String cookie = trustedTokenService.encodeCookie("ieb");
    Thread.sleep(10L);
    trustedTokenService.refreshToken(response, cookie, "ieb");
    verify();
  }

  @Test
  public void testAddCookie() {
    ComponentContext context = configureForCookie();
    HttpServletResponse response = createMock(HttpServletResponse.class);
    Capture<Cookie> cookieCapture = new Capture<Cookie>();
    response.addCookie(EasyMock.capture(cookieCapture));
    EasyMock.expectLastCall();
    
    replay();
    trustedTokenService.activate(context); 
    trustedTokenService.addCookie(response, "ieb");
    Assert.assertTrue(cookieCapture.hasCaptured());
    Cookie cookie = cookieCapture.getValue();
    Assert.assertNotNull(cookie);
    Assert.assertEquals("secure-cookie", cookie.getName());
    String user = trustedTokenService.decodeCookie(cookie.getValue());
    Assert.assertEquals("ieb", user);
    verify();
  }

  @Test
  public void testInjectCookiePrincipal() {
    ComponentContext context = configureForCookie();
    HttpServletRequest request = createMock(HttpServletRequest.class);
    Principal principal = createMock(Principal.class);
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal);
    EasyMock.expect(principal.getName()).andReturn("ieb");
    HttpServletResponse response = createMock(HttpServletResponse.class);
    Capture<Cookie> cookieCapture = new Capture<Cookie>();
    response.addCookie(EasyMock.capture(cookieCapture));
    EasyMock.expectLastCall();
    
    replay();
    trustedTokenService.activate(context); 
    trustedTokenService.injectToken(request, response);
    Assert.assertTrue(cookieCapture.hasCaptured());
    Cookie cookie = cookieCapture.getValue();
    Assert.assertNotNull(cookie);
    Assert.assertEquals("secure-cookie", cookie.getName());
    String user = trustedTokenService.decodeCookie(cookie.getValue());
    Assert.assertEquals("ieb", user);
    verify();
  }

  @Test
  public void testInjectCookieUser() {
    ComponentContext context = configureForCookie();
    HttpServletRequest request = createMock(HttpServletRequest.class);
    Principal principal = createMock(Principal.class);
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal);
    EasyMock.expect(principal.getName()).andReturn(null);
    EasyMock.expect(request.getRemoteUser()).andReturn("ieb");
    HttpServletResponse response = createMock(HttpServletResponse.class);
    Capture<Cookie> cookieCapture = new Capture<Cookie>();
    response.addCookie(EasyMock.capture(cookieCapture));
    EasyMock.expectLastCall();
    
    replay();
    trustedTokenService.activate(context); 
    trustedTokenService.injectToken(request, response);
    Assert.assertTrue(cookieCapture.hasCaptured());
    Cookie cookie = cookieCapture.getValue();
    Assert.assertNotNull(cookie);
    Assert.assertEquals("secure-cookie", cookie.getName());
    String user = trustedTokenService.decodeCookie(cookie.getValue());
    Assert.assertEquals("ieb", user);
    verify();
  }

  
  
  @Test
  public void testDropCredentials() {
    ComponentContext context = configureForCookie();
    HttpServletRequest request = createMock(HttpServletRequest.class);
    HttpServletResponse response = createMock(HttpServletResponse.class);
    Capture<Cookie> cookieCapture = new Capture<Cookie>();
    response.addCookie(EasyMock.capture(cookieCapture));
    EasyMock.expectLastCall();
    
    replay();
    trustedTokenService.activate(context); 
    trustedTokenService.dropCredentials(request, response);
    Assert.assertTrue(cookieCapture.hasCaptured());
    Cookie cookie = cookieCapture.getValue();
    Assert.assertNotNull(cookie);
    Assert.assertEquals("secure-cookie", cookie.getName());
    String user = trustedTokenService.decodeCookie(cookie.getValue());
    Assert.assertNull(user);
    verify();
  }
  

  @Test
  public void testDropCredentialsSession() {
    ComponentContext context = configureForSession();
    HttpServletRequest request = createMock(HttpServletRequest.class);
    HttpSession session = createMock(HttpSession.class);
    EasyMock.expect(request.getSession(false)).andReturn(session);
    session.setAttribute(TrustedTokenService.SA_AUTHENTICATION_CREDENTIALS, null);
    EasyMock.expectLastCall();

    HttpServletResponse response = createMock(HttpServletResponse.class);
    
    replay();
    trustedTokenService.activate(context); 
    trustedTokenService.dropCredentials(request, response);
    verify();
  }

  
  @Test
  public void testGetCredentialsNone() {
    ComponentContext context = configureForCookie();
    HttpServletRequest request = createMock(HttpServletRequest.class);
    Cookie[] cookies = new Cookie[] {
      new Cookie("sdfsd", "fsdfs"),  
      new Cookie("sdfsd1", "fsdfs"),  
      new Cookie("sdfsd2", "fsdfs"),  
      new Cookie("sdfsd3", "fsdfs"),  
      new Cookie("sdfsd4", "fsdfs"),  
    };
    EasyMock.expect(request.getCookies()).andReturn(cookies);
    HttpServletResponse response = createMock(HttpServletResponse.class);
    
    replay();
    trustedTokenService.activate(context); 
    Credentials none = trustedTokenService.getCredentials(request, response);
    Assert.assertNull(none);
    verify();
  }


  @Test
  public void testGetCredentialsValid() {
    ComponentContext context = configureForCookie();
    HttpServletRequest request = createMock(HttpServletRequest.class);
    Cookie[] cookies = new Cookie[] {
      new Cookie("sdfsd", "fsdfs"),  
      new Cookie("sdfsd1", "fsdfs"),  
      null,  
      new Cookie("sdfsd3", "fsdfs"),  
      new Cookie("sdfsd4", "fsdfs"),  
    };
    EasyMock.expect(request.getCookies()).andReturn(cookies);
    HttpServletResponse response = createMock(HttpServletResponse.class);
    
    replay();
    trustedTokenService.activate(context); 
    Cookie secureCookie = new Cookie("secure-cookie", trustedTokenService.encodeCookie("ieb"));
    cookies[2] = secureCookie;
    Credentials ieb = trustedTokenService.getCredentials(request, response);
    Assert.assertTrue( ieb instanceof SimpleCredentials );
    SimpleCredentials sc = (SimpleCredentials) ieb;
    TrustedUser tu = (TrustedUser) sc.getAttribute(TrustedTokenService.CA_AUTHENTICATION_USER);
    Assert.assertNotNull(tu);
    Assert.assertEquals("ieb",tu.getUser());
    verify();
  }


  @Test
  public void testGetCredentialsValidSession() {
    ComponentContext context = configureForSession();
    HttpServletRequest request = createMock(HttpServletRequest.class);
    HttpSession session = createMock(HttpSession.class);
    EasyMock.expect(request.getSession(true)).andReturn(session);
    
    Principal principal = createMock(Principal.class);
    EasyMock.expect(request.getUserPrincipal()).andReturn(principal);
    EasyMock.expect(principal.getName()).andReturn(null);
    EasyMock.expect(request.getRemoteUser()).andReturn("ieb");
    Capture<SimpleCredentials> attributeValue = new Capture<SimpleCredentials>();
    Capture<String> attributeName = new Capture<String>();
    session.setAttribute(EasyMock.capture(attributeName), EasyMock.capture(attributeValue));
    
    
    HttpServletResponse response = createMock(HttpServletResponse.class);
    
    replay();
    trustedTokenService.activate(context); 
    trustedTokenService.injectToken(request, response);
    Assert.assertTrue(attributeName.hasCaptured());
    Assert.assertTrue(attributeValue.hasCaptured());
    Credentials credentials = attributeValue.getValue();
    
    verify();
    reset();

    EasyMock.expect(request.getSession(false)).andReturn(session);
    EasyMock.expect(session.getAttribute(TrustedTokenService.SA_AUTHENTICATION_CREDENTIALS)).andReturn(credentials);
    
    replay();
    Credentials ieb = trustedTokenService.getCredentials(request, response);
    Assert.assertTrue( ieb instanceof SimpleCredentials );
    SimpleCredentials sc = (SimpleCredentials) ieb;
    TrustedUser tu = (TrustedUser) sc.getAttribute(TrustedTokenService.CA_AUTHENTICATION_USER);
    Assert.assertNotNull(tu);
    Assert.assertEquals("ieb",tu.getUser());
    verify();
  }


  public <T> T createMock(Class<T> mockClass) {
    T m = EasyMock.createMock(mockClass);
    mocks.add(m);
    return m;
  }
  
  public void replay() {
    EasyMock.replay(mocks.toArray());
  }
  
  public void verify() {
    EasyMock.verify(mocks.toArray());
  }
  
  public void reset() {
    EasyMock.reset(mocks.toArray());
  }
  
}
