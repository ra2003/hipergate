<%@ page import="java.util.Date,java.text.SimpleDateFormat,java.io.IOException,java.net.URLDecoder,java.sql.SQLException,java.sql.ResultSet,java.sql.Timestamp,java.sql.PreparedStatement,com.knowgate.jdc.JDCConnection,com.knowgate.dataobjs.*,com.knowgate.acl.*,com.knowgate.addrbook.Meeting,com.knowgate.crm.Company,com.knowgate.crm.Contact,com.knowgate.crm.Oportunity,com.knowgate.hipergate.Address,com.knowgate.hipergate.RecentlyUsed,com.knowgate.misc.Environment,com.knowgate.misc.Gadgets,com.knowgate.http.portlets.HipergatePortletConfig" language="java" session="false" contentType="text/html;charset=UTF-8" %>
<%@ include file="../methods/dbbind.jsp" %><%@ include file="../methods/cookies.jspf" %><%@ include file="../methods/authusrs.jspf" %><%@ include file="../methods/clientip.jspf" %><%@ include file="../methods/reqload.jspf" %><%@ include file="../methods/nullif.jspf" %><%
/*
  Copyright (C) 2003-2005  Know Gate S.L. All rights reserved.
                           C/Oña, 107 1º2 28050 Madrid (Spain)

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions
  are met:

  1. Redistributions of source code must retain the above copyright
     notice, this list of conditions and the following disclaimer.

  2. The end-user documentation included with the redistribution,
     if any, must include the following acknowledgment:
     "This product includes software parts from hipergate
     (http://www.hipergate.org/)."
     Alternately, this acknowledgment may appear in the software itself,
     if and wherever such third-party acknowledgments normally appear.

  3. The name hipergate must not be used to endorse or promote products
     derived from this software without prior written permission.
     Products derived from this software may not be called hipergate,
     nor may hipergate appear in their name, without prior written
     permission.

  This library is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

  You should have received a copy of hipergate License with this code;
  if not, visit http://www.hipergate.org or mail to info@hipergate.org
*/

  response.addHeader ("Pragma", "no-cache");
  response.addHeader ("cache-control", "no-store");
  response.setIntHeader("Expires", 0);

  /* Autenticate user cookie */
  if (autenticateSession(GlobalDBBind, request, response)<0) return;

  final int CollabTools = 17;

  int iAppMask = Integer.parseInt(getCookie(request, "appmask", "0"));
  
  String sIdDomain = getCookie(request,"domainid","");
  String sGuWorkArea = request.getParameter("gu_workarea");
  String sGuWriter = request.getParameter("gu_writer");
  String sGuCompany = request.getParameter("gu_company");
  String sGuContact = request.getParameter("gu_contact");
  String sGuAddress = request.getParameter("gu_address");
  String sLegalNm = request.getParameter("nm_legal");
  boolean bCreateMeeting = nullif(request.getParameter("chk_meeting")).equals("1");
  short iPrivate = Short.parseShort(request.getParameter("bo_private"));
  Company oCompany = new Company();
  Contact oContact = new Contact();
  Oportunity oOportunity = new Oportunity();
  Address oAddress = new Address();
  String gu_meeting;

  JDCConnection oConn = null;
  
  try {
    oConn = GlobalDBBind.getConnection("oportunity_new_store"); 

    oConn.setAutoCommit (false);
  
    if (sLegalNm.trim().length()>0) {
      if (sGuCompany.length()>0) {
        oCompany.put(DB.gu_company, sGuCompany);
      } else {
        sGuCompany = nullif(Company.getIdFromName(oConn, sLegalNm, sGuWorkArea));
        if (sGuCompany.length()==0) {
          oCompany.put(DB.gu_workarea, sGuWorkArea);
          oCompany.put(DB.nm_legal, sLegalNm);
          oCompany.put(DB.nm_commercial, sLegalNm);
          oCompany.store(oConn);
        }
      }
    }
    
    if (sGuContact.length()>0) {
      oContact.load(oConn, new Object[]{sGuContact});
      oContact.replace(DB.gu_workarea, sGuWorkArea);
      oContact.replace(DB.tx_name, request.getParameter("tx_name"));
      oContact.replace(DB.tx_surname, request.getParameter("tx_surname"));
      oContact.replace(DB.gu_writer, sGuWriter);    
    } else {
      oContact.put(DB.gu_workarea, sGuWorkArea);
      oContact.put(DB.tx_name, request.getParameter("tx_name"));
      oContact.put(DB.tx_surname, request.getParameter("tx_surname"));
      oContact.put(DB.bo_private, iPrivate);
      oContact.put(DB.gu_writer, sGuWriter);    
    }
    if (sGuCompany.length()>0) {
      oContact.put(DB.gu_company, sGuCompany);
    }
    oContact.store(oConn);

    if (request.getParameter("gu_address").length()==0) {
      loadRequest(oConn, request, oAddress);
      oAddress.put(DB.ix_address, Address.nextLocalIndex(oConn, DB.k_x_contact_addr, DB.gu_contact, oContact.getString(DB.gu_contact)));
      oAddress.store(oConn);      
      oContact.addAddress(oConn, oAddress.getString(DB.gu_address));
    } else {
      oAddress.load(oConn, new Object[]{request.getParameter("gu_address")});
      loadRequest(oConn, request, oAddress);
      oAddress.store(oConn);      
    }

    loadRequest(oConn, request, oOportunity);
    oOportunity.put(DB.gu_contact, oContact.getString(DB.gu_contact));
    if (sGuCompany.length()>0) oOportunity.replace(DB.gu_company, sGuCompany);
    oOportunity.store(oConn);

    String gu_contact = sGuContact;
    String id_user = sGuWriter;
    String gu_workarea = sGuWorkArea;
    String id_domain = sIdDomain;
    String dt_next_action = request.getParameter("dt_next_action");
    ResultSet oRSet;
    
%><%@ include file="oportunity_create_meeting.jspf" %><%

    // **************************
    // Update recently used lists
    
    DBPersist oItem;
    if (oCompany.getStringNull(DB.gu_company,"").length()>0) {
      oItem = new DBPersist (DB.k_companies_recent, "RecentCompany");
      oItem.put (DB.gu_company, oCompany.getString(DB.gu_company));
      oItem.put (DB.gu_user, sGuWriter);
      oItem.put (DB.gu_workarea, sGuWorkArea);
      oItem.put (DB.nm_company, sLegalNm);
      if (oAddress.getItemMap().containsKey(DB.work_phone))
        oItem.put (DB.work_phone, oAddress.get(DB.work_phone));
      if (oAddress.getItemMap().containsKey(DB.tx_email))
        oItem.put (DB.tx_email, oAddress.get(DB.tx_email));	  
      new RecentlyUsed (DB.k_companies_recent, 10, DB.gu_company, DB.gu_user).add (oConn, oItem);
    }

    oItem = new DBPersist (DB.k_contacts_recent, "RecentContact");
    oItem.put (DB.gu_contact, oContact.getString(DB.gu_contact));
    oItem.put (DB.full_name, oContact.getStringNull(DB.tx_name,"") + " " + oContact.getStringNull(DB.tx_surname,""));
    oItem.put (DB.gu_user, sGuWriter);
    oItem.put (DB.gu_workarea, sGuWorkArea);
    oItem.put (DB.nm_company, sLegalNm);
    if (oAddress.getItemMap().containsKey(DB.work_phone))
      oItem.put (DB.work_phone, oAddress.get(DB.work_phone));
    if (oAddress.getItemMap().containsKey(DB.tx_email))
      oItem.put (DB.tx_email, oAddress.get(DB.tx_email));
    new RecentlyUsed (DB.k_contacts_recent, 10, DB.gu_contact, DB.gu_user).add (oConn, oItem);

    // ***************************************************************************
    // Check whether or not there is an active LDAP server and synchronize with it
    
    String sLdapConnect = Environment.getProfileVar(GlobalDBBind.getProfileName(),"ldapconnect", "");

    if (sLdapConnect.length()>0) {
      Class oLdapCls = Class.forName(Environment.getProfileVar(GlobalDBBind.getProfileName(),"ldapclass", "com.knowgate.ldap.LDAPNovell"));

      com.knowgate.ldap.LDAPModel oLdapImpl = (com.knowgate.ldap.LDAPModel) oLdapCls.newInstance();

      oLdapImpl.connectAndBind(Environment.getProfile(GlobalDBBind.getProfileName()));
      
      // If address already exists delete it before re-inserting
      if (sGuAddress.length()>0) {
        try {
          oLdapImpl.deleteAddress (oConn, oAddress.getString(DB.gu_address));
        } catch (com.knowgate.ldap.LDAPException ignore) { }
      }

      if (!oAddress.isNull(DB.tx_email))
        oLdapImpl.addAddress (oConn, oAddress.getString(DB.gu_address));

      oLdapImpl.disconnect();
    } // fi (ldapconnect!="")

    // End LDAP synchronization
    // ***************************************************************************

    oConn.commit();
    oConn.close("oportunity_new_store");
  }
  catch (SQLException e) {  
    disposeConnection(oConn,"oportunity_new_store");
    oConn = null;
    response.sendRedirect (response.encodeRedirectUrl ("../common/errmsg.jsp?title=SQLException&desc=" + e.getLocalizedMessage() + "&resume=_back"));
  }
  catch (NumberFormatException e) {
    disposeConnection(oConn,"oportunity_new_store");
    oConn = null;
    response.sendRedirect (response.encodeRedirectUrl ("../common/errmsg.jsp?title=NumberFormatException&desc=" + e.getMessage() + "&resume=_back"));
  }
  
  if (null==oConn) return;
  
  oConn = null;
  
  // Refresh parent and close window, or put your own code here
  out.write ("<HTML><HEAD><TITLE>Wait...</TITLE><" + "SCRIPT LANGUAGE='JavaScript' TYPE='text/javascript'>window.opener.location.reload(true); self.close();<" + "/SCRIPT" +"></HEAD></HTML>");
%>