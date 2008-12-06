<%@ page import="java.io.IOException,java.net.URLDecoder,java.sql.SQLException,com.knowgate.jdc.JDCConnection,com.knowgate.acl.*,com.knowgate.dataobjs.*,com.knowgate.misc.Gadgets" language="java" session="false" contentType="text/html;charset=UTF-8" %>
<%@ include file="../methods/dbbind.jsp" %><%@ include file="../methods/cookies.jspf" %><%@ include file="../methods/authusrs.jspf" %><%@ include file="../methods/nullif.jspf" %><%

/*
  Copyright (C) 2003  Know Gate S.L. All rights reserved.
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

  if (autenticateSession(GlobalDBBind, request, response)<0) return;

  String sWorkArea = getCookie(request,"workarea", "");
  String sWhere = nullif(request.getParameter("where"));
  int iSkip = Integer.parseInt(nullif(request.getParameter("skip"),"0"));
  int iMaxRows = Integer.parseInt(nullif(request.getParameter("maxrows"),"100"));

  String nm_table = request.getParameter("nm_table");
  String tp_control = request.getParameter("tp_control");
  String nm_control = request.getParameter("nm_control");
  String nm_coding = request.getParameter("nm_coding");
  String ix_form = nullif(request.getParameter("ix_form"),"0");
  String nm_input;
  
  
  if (null==nm_control) {
    response.sendRedirect (response.encodeRedirectUrl ("errmsg.jsp?title=Error&desc=No value was set for control field at base form "+nm_table+"("+nm_coding+")&resume=_close"));
    return;  
  }

  if (nm_control.indexOf(" AS ")>0)
    nm_input = nm_control.substring(nm_control.indexOf(" AS ")+4).trim();
  else
    nm_input = nm_control;
  
  if (null==nm_input) {
    response.sendRedirect (response.encodeRedirectUrl ("errmsg.jsp?title=Error&desc=No value was set for output field at base form "+nm_table+"("+nm_coding+")&resume=_close"));
    return;
  }
%>
<!-- +-----------------------+ -->
<!-- | Listado de Referencia | -->
<!-- | ¨ KnowGate 2003-2005  | -->
<!-- +-----------------------+ -->
<HTML>
  <HEAD>
    <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=utf-8">
    <TITLE>hipergate :: Reference Listing</TITLE>
    <SCRIPT LANGUAGE="JavaScript" SRC="../javascript/cookies.js"></SCRIPT>
    <SCRIPT LANGUAGE="JavaScript" SRC="../javascript/setskin.js"></SCRIPT>
    <SCRIPT LANGUAGE="JavaScript1.2" SRC="../javascript/combobox.js"></SCRIPT>    
    <SCRIPT LANGUAGE="JavaScript1.2" SRC="../javascript/findit.js"></SCRIPT> 
    <SCRIPT LANGUAGE="JavaScript" TYPE="text/javascript">
      <!--
            
      function choose(vlstr,nmstr) {
        var prnt = window.parent;
	var frm = prnt.opener.document.forms[<%=ix_form%>];
	var opt;	
        <% 
             
           if (tp_control.equals("1"))
             // El control de entrada es de tipo TEXT
             out.write("	frm." + nm_input + ".value = unescape(nmstr);\n");           
           else {
             // El control de entrada es de tipo SELECT
             out.write("if (-1==comboIndexOf(frm." + nm_input + ",vlstr)) {\n");             
             out.write("          // La llamada directa a new Option(...) falla en IE 6.0 cuando se crea la OPTION desde el documento actual pero se intenta agregar a la SELECT de otro documento\n");
             out.write("          if (navigator.appName==\"Microsoft Internet Explorer\") {\n");
             out.write("            opt = prnt.opener.document.createElement(\"OPTION\");\n");
             out.write("            opt.text = unescape(nmstr);\n");
             out.write("            opt.value = vlstr;\n");
             out.write("            frm." + nm_control + ".options.add(opt);\n");                          
             out.write("          }\n");
             out.write("          else\n");
             out.write("            frm." + nm_input + ".options[frm." + nm_input + ".length] = new Option(unescape(nmstr), vlstr);\n");
             out.write("        } // fi(comboIndexOf())\n");
             out.write("        setCombo(frm." + nm_control + ",unescape(nmstr));\n");
           }
           if (nm_coding.length()>0)
             out.write("        frm." + nm_coding + ".value = vlstr;\n");
        %>        
        prnt.close();
      }      
      //-->
    </SCRIPT>
  </HEAD>
  <BODY SCROLLING="yes" TOPMARGIN="4" MARGINHEIGHT="4" LEFTMARGIN="4" RIGHTMARGIN="4">
    <FORM>
      <IMG SRC="../images/images/find16.gif">&nbsp;<INPUT TYPE="text" MAXLENGTH="50" NAME="sought">&nbsp;<A CLASS="linkplain" HREF="javascript:findit(document.forms[0].sought.value);">Search</A>
    </FORM>
    <TABLE WIDTH="100%" BORDER="0" CELLSPACING="0" CELLPADDING="0">
<%  
  int iOdPos = 0;
  JDCConnection oConn = null;
  Object aParams[] = { sWorkArea };
  int iLookup = -1;
  DBSubset oLookup = null;
  
  try {

    oConn = GlobalDBBind.getConnection("reference");

    switch (oConn.getDataBaseProduct()) {
      case JDCConnection.DBMS_MYSQL :
        nm_control = Gadgets.replace (nm_control, "(\\w+)\\x2B\\x27\\x20\\x27\\x2B(\\w+)\\x20AS\\x20(\\w+)", "CONCAT($1,' ',$2) AS $3");
        break;
      case JDCConnection.DBMS_ORACLE :
      case JDCConnection.DBMS_POSTGRESQL :
        nm_control = Gadgets.replace (nm_control, "\\+", "||");
        break;
    }
     
    oLookup = new DBSubset (nm_table,
    			    (nm_coding.length()>0 ? nm_coding : "NULL") + "," + nm_control,    			    
      			    DB.gu_workarea + "=? " + (sWhere.length()>0 ? " AND " + sWhere : "") + " ORDER BY 2", 100);
    oLookup.setMaxRows(iMaxRows);
    if (iSkip>0)
      iLookup = oLookup.load (oConn, aParams, iSkip);
    else
      iLookup = oLookup.load (oConn, aParams);
    
    oConn.close("reference");

    for (iOdPos=0; iOdPos<iLookup; iOdPos++) {
      out.write ("      <TR><TD CLASS=\"strip" + String.valueOf(iOdPos%2+1) + "\"><A HREF='#' onclick='choose(\"" + oLookup.getString(0,iOdPos) + "\",\""+ Gadgets.URLEncode(oLookup.getString(1,iOdPos)) + "\")' CLASS='linkplain'>" + oLookup.getString(1,iOdPos) + "<A></TD></TR>\n");
    } // next (i)
    
  }
  catch (SQLException e) {
    if (null!=oConn)
      if (!oConn.isClosed()) {
        oConn.close("reference");
        oConn = null;
      }        
    response.sendRedirect (response.encodeRedirectUrl ("errmsg.jsp?title=Error&desc=" + e.getLocalizedMessage() + "&resume=_close"));  
  }
  if (null==oConn) return;
  oConn=null;
%>
    </TABLE>
<%
  if (iLookup>0) {
    out.write("<BR>");
    if (iSkip>0) // If iSkip>0 then we have prev items
      out.write("            <A HREF=\"reference.jsp?nm_table=" + nm_table + "&tp_control=" + tp_control + "&nm_control=" + nm_control + "&nm_coding=" + nm_coding + "&skip=" + String.valueOf(iSkip-iMaxRows) + "&where=" + Gadgets.URLEncode(sWhere) + "\" CLASS=\"linkplain\">&lt;&lt;&nbsp;<B>Previous</B>" + "</A>&nbsp;&nbsp;&nbsp;");    
    if (!oLookup.eof())
      out.write("            <A HREF=\"reference.jsp?nm_table=" + nm_table + "&tp_control=" + tp_control + "&nm_control=" + nm_control + "&nm_coding=" + nm_coding + "&skip=" + String.valueOf(iSkip+iMaxRows) + "&where=" + Gadgets.URLEncode(sWhere) + "\" CLASS=\"linkplain\">&nbsp;<B>Next</B>" + "&nbsp;&nbsp;&gt;&gt;</A>");
   }
%>
    <FORM>
    <CENTER><INPUT TYPE="button" CLASS="closebutton" onClick="choose('','')" VALUE="Close" ACCESSKEY="ALT+c" TITLE="ALT+c"></CENTER>
    </FORM>
  </BODY>
</HTML>
