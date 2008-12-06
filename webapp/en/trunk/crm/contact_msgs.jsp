<%@ page import="java.util.Vector,java.math.BigDecimal,java.net.URLDecoder,javax.mail.internet.MimeUtility,java.sql.SQLException,java.sql.PreparedStatement,java.sql.ResultSet,com.knowgate.acl.*,com.knowgate.jdc.JDCConnection,com.knowgate.dataobjs.DB,com.knowgate.dataobjs.DBBind,com.knowgate.dataobjs.DBSubset,com.knowgate.misc.Environment,com.knowgate.misc.Gadgets,com.knowgate.hipergate.Category,com.knowgate.hipermail.*" language="java" session="false" contentType="text/html;charset=UTF-8" %>
<%@ include file="../methods/page_prolog.jspf" %><%@ include file="../methods/dbbind.jsp" %><%@ include file="../methods/cookies.jspf" %><%@ include file="../methods/authusrs.jspf" %><%@ include file="../methods/nullif.jspf" %><%
/*
  Copyright (C) 2004  Know Gate S.L. All rights reserved.
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

  response.addHeader ("cache-control", "no-cache");

  String sLanguage = getNavigatorLanguage(request);  
  String sSkin = getCookie(request, "skin", "xp");

  String id_user = getCookie(request,"userid","");
  String id_domain = getCookie(request,"domainid","0");
  String gu_workarea = getCookie(request,"workarea",""); 
  
  String gu_folder = request.getParameter("gu_folder");
  String screen_width = request.getParameter("screen_width");
  String gu_contact = request.getParameter("gu_contact");
  String tp_recipient = nullif(request.getParameter("tp_recipient"), "from");
  String subject = nullif(request.getParameter("subject"));
  String dt_before = nullif(request.getParameter("dt_before"));
  String dt_after = nullif(request.getParameter("dt_after"));

  int iScreenWidth;
  float fScreenRatio;

  if (screen_width==null)
    iScreenWidth = 800;
  else if (screen_width.length()==0)
    iScreenWidth = 800;
  else
    iScreenWidth = Integer.parseInt(screen_width);
  
  fScreenRatio = ((float) iScreenWidth) / 800f;
  if (fScreenRatio<1) fScreenRatio=1;

  // **********************************************

  int iMailCount = 0;
  String sOrderBy;
  int iMaxRows;
  int iSkip;
  
  try {
    if (request.getParameter("maxrows")!=null)
      iMaxRows = Integer.parseInt(request.getParameter("maxrows"));
    else 
      iMaxRows = Integer.parseInt(getCookie(request, "maxrows", "100"));
  }
  catch (NumberFormatException nfe) { iMaxRows = 100; }
  
  if (request.getParameter("skip")!=null)
    iSkip = Integer.parseInt(request.getParameter("skip"));      
  else
    iSkip = 0;
    
  if (iSkip<0) iSkip = 0;

  // **********************************************
  
  if (request.getParameter("orderby")!=null)
    sOrderBy = request.getParameter("orderby");
  else
    sOrderBy = "6,7 DESC";

  Vector vParams = new Vector();
  vParams.add(gu_workarea);
  
  String sWhere;
  
  if (tp_recipient.equals("from")) {
    sWhere = "m." + DB.gu_workarea + "=? AND m." + DB.bo_deleted + "<>1 AND m." + DB.gu_parent_msg + " IS NULL AND " +
             DB.gu_mimemsg + " IN (SELECT " + DB.gu_mimemsg + " FROM " + DB.k_inet_addrs + " WHERE " + DB.tp_recipient + "='from' AND " + DB.gu_contact + "=?) AND " +
             DB.gu_mimemsg + " IN (SELECT " + DB.gu_mimemsg + " FROM " + DB.k_inet_addrs + " WHERE (" + DB.tp_recipient + "='to' OR " + DB.tp_recipient + "='cc' OR " + DB.tp_recipient + "='bcc') AND " + DB.gu_user + "=?) ";
    vParams.add(gu_contact);
    vParams.add(id_user);  
  } else {
    sWhere = "m." + DB.gu_workarea + "=? AND m." + DB.bo_deleted + "<>1 AND m." + DB.gu_parent_msg + " IS NULL AND " +
             DB.gu_mimemsg + " IN (SELECT " + DB.gu_mimemsg + " FROM " + DB.k_inet_addrs + " WHERE " + DB.tp_recipient + "='from' AND " + DB.gu_user + "=?) AND " +
             DB.gu_mimemsg + " IN (SELECT " + DB.gu_mimemsg + " FROM " + DB.k_inet_addrs + " WHERE (" + DB.tp_recipient + "='to' OR " + DB.tp_recipient + "='cc' OR " + DB.tp_recipient + "='bcc') AND " + DB.gu_contact + "=?) ";
    vParams.add(id_user);
    vParams.add(gu_contact);
  }

  if (subject.length()!=0) {
    sWhere += " AND " + DB.tx_subject + " " + DBBind.Functions.ILIKE + " ?";
    vParams.add("%"+subject+"%");
  }
  if (dt_before.length()!=0) {
    sWhere += " AND " + DB.dt_received + ">={ ts '"+dt_before+" 00:00:00'}";
  }
  if (dt_after.length()!=0) {
    sWhere += " AND " + DB.dt_received + "<={ ts '"+dt_after+" 23:59:59'}";
  }
  
  ACLUser oMe = new ACLUser();
  Category oFolder = new Category();
  String sFolderName = "";
  JDCConnection oConn = null;
  DBSubset oMsgs = null;
  int iMsgs = 0;
  String sOutBox = null, sDrafts = null;
   
  try {
    oConn = GlobalDBBind.getConnection("msg_search");

      oMe.load(oConn, new Object[]{id_user});

      sOutBox = oMe.getMailFolder(oConn, "outbox");
      sDrafts = oMe.getMailFolder(oConn, "drafts");
    
      oMsgs = new DBSubset (DB.k_mime_msgs + " m", DB.gu_mimemsg+","+DB.id_message+","+DB.id_priority+","+DB.nm_from+","+DB.nm_to+","+DB.tx_subject+","+DB.dt_received+","+DB.dt_sent+","+DB.len_mimemsg+","+DB.pg_message+","+DB.gu_category,
      			    sWhere + " AND m." + DB.gu_category + "<>'"+sOutBox+"' AND m."+DB.gu_category+"<>'"+sDrafts+"' ORDER BY " + sOrderBy, 100);
      
      iMsgs = oMsgs.load(oConn, vParams.toArray());
          
    oConn.close("msg_search");
  }
  catch (SQLException e) {
    if (oConn!=null)
      if (!oConn.isClosed())
        oConn.close("msg_search");
    oConn = null;    
    response.sendRedirect (response.encodeRedirectUrl ("../common/errmsg.jsp?title=SQLException&desc=" + e.getMessage() + "&resume=_back"));
  }
  
  if (null==oConn) return;
  oConn = null;
%>

<HTML LANG="<% out.write(sLanguage); %>">
<HEAD>
  <TITLE>Sent and received messages</TITLE>
  <SCRIPT LANGUAGE="JavaScript" TYPE="text/javascript" SRC="../javascript/cookies.js"></SCRIPT>  
  <SCRIPT LANGUAGE="JavaScript" TYPE="text/javascript" SRC="../javascript/setskin.js"></SCRIPT>
  <SCRIPT LANGUAGE="JavaScript" TYPE="text/javascript" SRC="../javascript/combobox.js"></SCRIPT>
  <SCRIPT LANGUAGE="JavaScript" TYPE="text/javascript" SRC="../javascript/datefuncs.js"></SCRIPT>  
  <SCRIPT LANGUAGE="JavaScript" TYPE="text/javascript" DEFER="defer">
    <!--

      // ------------------------------------------------------

        function showCalendar(ctrl) {       
          var dtnw = new Date();

          window.open("../common/calendar.jsp?a=" + (dtnw.getFullYear()) + "&m=" + dtnw.getMonth() + "&c=" + ctrl, "", "toolbar=no,directories=no,menubar=no,resizable=no,width=171,height=195");
        } // showCalendar()

      // ------------------------------------------------------

	    function viewMessage(fld,num,id) {
	      open ("../hipermail/msg_view.jsp?mailincoming=localhost&mailbox=<%=id_user%>&nm_folder="+fld+"&nu_msg=" + String(num) + "&id_msg=" + escape(id) + "&resume=" + escape("number="+String(num)+"&msgid="+id), "editmailmsg"+String(num), "scrollbars=no,resizable=yes,directories=no,toolbar=no,menubar=no,width=" + String(600*screen.width/800) + ",height=" + String(460*screen.height/640));	
	      return false;
	    } // viewMessage
      
      // ------------------------------------------------------

	function sortBy(fld) {
	  
	  var frm = document.forms[0];
	
	  frm.orderby.value = String(fld);
	  
	  frm.submit();
	  
	} // sortBy		

      // ------------------------------------------------------

	function validate() {
	  var frm = document.forms[0];

	  if (!isDate(frm.dt_before.value, "d") && frm.dt_before.value.length>0) {
	    alert ("Start date is not valid");
	    return false;
	  }

	  if (!isDate(frm.dt_after.value, "d") && frm.dt_after.value.length>0) {
	    alert ("End date is not valid");
	    return false;
	  }
	
	  if (frm.chk_recipient[0].checked)
	    frm.tp_recipient.value = "from";
	  else
	    frm.tp_recipient.value = "to";
	  
	  return true;
	}
    //-->    
  </SCRIPT>
  <SCRIPT LANGUAGE="JavaScript" TYPE="text/javascript">
    <!--
	function setCombos() {
	  var frm = document.forms[0];
	  
	  frm.screen_width.value = String(screen.width);
	  
	} // setCombos()
    //-->    
  </SCRIPT>
</HEAD>
<BODY  TOPMARGIN="8" MARGINHEIGHT="8" onLoad="setCombos()">
    <DIV class="cxMnu1" style="width:290px"><DIV class="cxMnu2">
      <SPAN class="hmMnuOff" onMouseOver="this.className='hmMnuOn'" onMouseOut="this.className='hmMnuOff'" onClick="history.back()"><IMG src="../images/images/toolmenu/historyback.gif" width="16" style="vertical-align:middle" height="16" border="0" alt="Back"> Back</SPAN>
      <SPAN class="hmMnuOff" onMouseOver="this.className='hmMnuOn'" onMouseOut="this.className='hmMnuOff'" onClick="location.reload(true)"><IMG src="../images/images/toolmenu/locationreload.gif" width="16" style="vertical-align:middle" height="16" border="0" alt="Actualizar"> Actualizar</SPAN>
      <SPAN class="hmMnuOff" onMouseOver="this.className='hmMnuOn'" onMouseOut="this.className='hmMnuOff'" onClick="window.print()"><IMG src="../images/images/toolmenu/windowprint.gif" width="16" height="16" style="vertical-align:middle" border="0" alt="Imprimir"> Imprimir</SPAN>
    </DIV></DIV>
    <FORM METHOD="post" ACTION="contact_msgs.jsp" onsubmit="return validate()">
      <INPUT TYPE="hidden" NAME="id_domain" VALUE="<%=id_domain%>">
      <INPUT TYPE="hidden" NAME="gu_workarea" VALUE="<%=gu_workarea%>">
      <INPUT TYPE="hidden" NAME="gu_contact" VALUE="<%=gu_contact%>">
      <INPUT TYPE="hidden" NAME="tp_recipient" VALUE="<%=tp_recipient%>">
      <INPUT TYPE="hidden" NAME="maxrows" VALUE="<%=String.valueOf(iMaxRows)%>">
      <INPUT TYPE="hidden" NAME="skip" VALUE="<%=String.valueOf(iSkip)%>">
      <INPUT TYPE="hidden" NAME="screen_width">
      <INPUT TYPE="hidden" NAME="where" VALUE="<%=sWhere%>">
      <INPUT TYPE="hidden" NAME="orderby" VALUE="<%=sOrderBy%>">

      <TABLE CELLSPACING="2" CELLPADDING="2">
      <TR><TD COLSPAN="3" BACKGROUND="../images/images/loginfoot_med.gif" HEIGHT="3"></TD></TR>
      <TR>
        <TD>&nbsp;&nbsp;<IMG SRC="../images/images/new16x16.gif" WIDTH="16" HEIGHT="16" BORDER="0" ALT="New"></TD>
        <TD VALIGN="middle"><A HREF="../hipermail/msg_new_f.jsp?folder=drafts" TARGET="_blank" CLASS="linkplain">New</A></TD>
        <TD ALIGN="center">
          <INPUT TYPE="radio" NAME="chk_recipient" onclick="if (validate()) document.forms[0].submit()" <% if (tp_recipient.equals("from")) out.write("CHECKED"); %>><FONT CLASS="textplain">&nbsp;Recibidos&nbsp;&nbsp;</FONT>
          <INPUT TYPE="radio" NAME="chk_recipient" onclick="if (validate()) document.forms[0].submit()" <% if (tp_recipient.equals("to")) out.write("CHECKED"); %>><FONT CLASS="textplain">&nbsp;Enviados&nbsp;&nbsp;</FONT>
        </TD>
      </TR>
      <TR>
        <TD></TD>
        <TD><FONT CLASS="textplain">Asunto&nbsp;</FONT></TD>
        <TD>
                <INPUT TYPE="text" CLASS="combomini" NAME="subject" VALUE="<%=subject%>">
                <FONT CLASS="textplain">Entre&nbsp;</FONT>
                <INPUT TYPE="text" CLASS="combomini" NAME="dt_before" VALUE="<%=dt_before%>" MAXLENGTH="10" SIZE="11">
                <A HREF="javascript:showCalendar('dt_before')"><IMG SRC="../images/images/datetime16.gif" WIDTH="16" HEIGHT="16" BORDER="0" ALT="Ver Calendario"></A>
	              &nbsp;&nbsp;<FONT CLASS="textplain">y</FONT>&nbsp;&nbsp;
                <INPUT TYPE="text" CLASS="combomini" NAME="dt_after" VALUE="<%=dt_after%>" SIZE="11" MAXLENGTH="10">
                <A HREF="javascript:showCalendar('dt_after')"><IMG SRC="../images/images/datetime16.gif" WIDTH="16" HEIGHT="16" BORDER="0" ALT="Ver Calendario"></A>
		            <A HREF="#" onclick="document.forms[0].submit()" CLASS="linkplain">Find</A>            
        </TD>
      </TR>
      <TR><TD COLSPAN="3" BACKGROUND="../images/images/loginfoot_med.gif" HEIGHT="3"></TD></TR>
      </TABLE>
      <!-- End Top Menu -->
      <TABLE CELLSPACING="1" CELLPADDING="0">
        <TR>
          <TD COLSPAN="3" ALIGN="left">
<%

	String f150 = String.valueOf(floor(150f*fScreenRatio));
	String f320 = String.valueOf(floor(320f*fScreenRatio));

	String sFolder, sGuid, sId, sFrom, sTo, sSubject, sStrip, sSize, sPriority, sDateSent, sDateReceived;
	int iLen;
	BigDecimal pgMsg;
%>
          </TD>
        </TR>
        <TR>
          <TD CLASS="tableheader" BACKGROUND="../skins/<%=sSkin%>/tablehead.gif"><A HREF="javascript:sortBy(3);" oncontextmenu="return false;"><IMG SRC="../skins/<%=sSkin + (sOrderBy.equals("3") ? "/sortedfld.gif" : "/sortablefld.gif")%>" WIDTH="14" HEIGHT="10" BORDER="0" ALT="Order by priority"></A></TD>
          <TD CLASS="tableheader" WIDTH="<%=f150%>" BACKGROUND="../skins/<%=sSkin%>/tablehead.gif"><A HREF="javascript:sortBy(4);" oncontextmenu="return false;"><IMG SRC="../skins/<%=sSkin + (sOrderBy.equals("4") ? "/sortedfld.gif" : "/sortablefld.gif")%>" WIDTH="14" HEIGHT="10" BORDER="0" ALT="Order by sender"></A>&nbsp;<B>De</B></TD>
          <TD CLASS="tableheader" WIDTH="<%=f320%>" BACKGROUND="../skins/<%=sSkin%>/tablehead.gif"><A HREF="javascript:sortBy(6);" oncontextmenu="return false;"><IMG SRC="../skins/<%=sSkin + (sOrderBy.equals("6") ? "/sortedfld.gif" : "/sortablefld.gif")%>" WIDTH="14" HEIGHT="10" BORDER="0" ALT="Order by subject"></A>&nbsp;<B>Asunto</B></TD>
          <TD CLASS="tableheader" BACKGROUND="../skins/<%=sSkin%>/tablehead.gif"><A HREF="javascript:sortBy(7);" oncontextmenu="return false;"><IMG SRC="../skins/<%=sSkin + (sOrderBy.equals("7") ? "/sortedfld.gif" : "/sortablefld.gif")%>" WIDTH="14" HEIGHT="10" BORDER="0" ALT="Order by received date"></A>&nbsp;<B>Date</B></TD>
          <TD CLASS="tableheader" BACKGROUND="../skins/<%=sSkin%>/tablehead.gif"><A HREF="javascript:sortBy(9);" oncontextmenu="return false;"><IMG SRC="../skins/<%=sSkin + (sOrderBy.equals("9") ? "/sortedfld.gif" : "/sortablefld.gif")%>" WIDTH="14" HEIGHT="10" BORDER="0" ALT="Order by size"></A>&nbsp;<B>Size</B></TD>
        </TR>
<%
     for (int m=0; m<iMsgs; m++) {
         sStrip = String.valueOf((m%2)+1);
         
         sGuid = oMsgs.getString(0, m);
         sId = oMsgs.getString(1, m);
         sPriority = oMsgs.getStringNull(2, m, "3");
         sFrom = MimeUtility.decodeText(oMsgs.getStringNull(3, m, ""));
         sTo = MimeUtility.decodeText(oMsgs.getStringNull(4, m, ""));
         sSubject = MimeUtility.decodeText(oMsgs.getStringNull(5, m, "<I>&lt;sin asunto&gt;</I>"));
	 sDateReceived = nullif(oMsgs.getDateShort(6, m));
	 sDateSent = nullif(oMsgs.getDateShort(7, m));
	 if (!oMsgs.isNull(8,m))
           iLen = oMsgs.getInt(8, m);
         else
           iLen = -1;

         if (iLen==-1)
           sSize = "";
         else if (iLen<=1024)
           sSize = "1Kb";
         else
           sSize = String.valueOf(iLen/1024) + "Kb";	          
	 
	 pgMsg = oMsgs.getDecimal(9,m);
	 
	 sFolder = oMsgs.getString(10,m);
	   
         out.write("<TR>");         
         out.write("<TD CLASS=\"strip\""+sStrip+">");
         if (sPriority.startsWith("1"))
           out.write ("<IMG SRC=\"../images/images/hipermail/highp.gif\" WIDTH=\"10\" HEIGHT=\"18\" BORDER=\"0\">");
         else if (sPriority.startsWith("5"))
           out.write ("<IMG SRC=\"../images/images/hipermail/lowp.gif\" WIDTH=\"10\" HEIGHT=\"18\" BORDER=\"0\">");         
         out.write("</TD>");
         out.write("<TD CLASS=\"strip\""+sStrip+"><FONT CLASS=\"textplain\">"+sFrom+"</FONT></TD>");
         out.write("<TD CLASS=\"strip\""+sStrip+"><A HREF=\"#\" CLASS=\"linkplain\" onclick=\"viewMessage('"+sFolder+"',"+pgMsg.toString()+",'"+sId+"')\">"+sSubject+"</FONT></TD>");
         out.write("<TD CLASS=\"strip\""+sStrip+"><FONT CLASS=\"textplain\">"+sDateReceived+"</FONT></TD>");
         out.write("<TD CLASS=\"strip\""+sStrip+" ALIGN=\"right\"><FONT CLASS=\"textplain\">"+sSize+"</FONT></TD>");
         out.write("</TR>\n");         

       } // next (m)
%>  	  
      </TABLE>
    </FORM>
</BODY>
</HTML>
<%@ include file="../methods/page_epilog.jspf" %>