/*
  Copyright (C) 2010  Know Gate S.L. All rights reserved.

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

package com.knowgate.gdata;

import java.io.IOException;

import java.net.URL;
import java.net.MalformedURLException;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.PreparedStatement;

import java.util.Date;
import java.util.SimpleTimeZone;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;

import com.google.gdata.data.DateTime;
import com.google.gdata.data.PlainTextConstruct;
import com.google.gdata.data.extensions.When;
import com.google.gdata.data.extensions.BaseEventEntry.Visibility;

import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;

import com.google.gdata.data.calendar.EventWho;
import com.google.gdata.data.calendar.CalendarFeed;
import com.google.gdata.data.calendar.CalendarEventFeed;
import com.google.gdata.data.calendar.CalendarEntry;
import com.google.gdata.data.calendar.CalendarEventEntry;

import com.google.gdata.client.calendar.CalendarQuery;
import com.google.gdata.client.calendar.CalendarService;

import com.knowgate.misc.Gadgets;
import com.knowgate.debug.DebugFile;
import com.knowgate.dataobjs.DB;
import com.knowgate.dataobjs.DBSubset;
import com.knowgate.dataobjs.DBCommand;
import com.knowgate.addrbook.Meeting;
import com.knowgate.jdc.JDCConnection;
import com.knowgate.acl.PasswordRecord;
import com.knowgate.cache.DistributedCachePeer;

/**
 * Synchronize hipergate calendar with Google Calendar
 * @author Sergio Montoro Ten
 * @version 1.0
 */
public class GCalendarSynchronizer {

  private Integer iIdDomain;
  private String sEMail, sGuUser, sGuWorkArea;
  
  private CalendarService oCalSrv;
  private CalendarEntry oCalendar;
  private HashMap<String,Integer> oWrkA;
  
  public GCalendarSynchronizer() {
  	oCalSrv = new CalendarService("knowgate-hipergate-6");
    oWrkA = new HashMap<String,Integer>();
  }

  /**
   * <p>Conect to Google Calendar Service</p>
   * This method looks for a PasswordRecord at table k_user_pwd which id_pwd column value is 'gmail'.
   * If a PasswordRecord for gmail exists and its cal field has a non-empty value, then a connection
   * attempt with Google Calendar Service is made.
   * The matching between Google event objects and hipergate meeting objects is done with the
   * iCalendar Unique Identifier of the object retrieved with method CalendarEventEntry.getIcalUID()
   * from Google and with method Meeting.getString(DB.id_calendar) from hipergate.
   * If a new iCalendar UID must be generated by hipergate, this will be the GUID of the Meeting
   * concatenated with '@hipergate.org'.
   * The time zone for the dates will be the one of the Fellow corresponding to the ACLUser
   * with sUser GUID. If no time zone is set the GMT will be assumed.
   * @param oConn JDCConnection Opened JDBC database connection
   * @param sUser GUID of hipergate user (from k_users table)
   * @param sWorkArea GUID of hipergate calendar work area (from k_workareas table)
   * @param DistributedCachePeer Optional instance of a cache for reducing database accesses
   * @return true if connection was successfull, false otherwise.
   * @throws IOException
   * @throws SQLException
   * @throws MalformedURLException
   * @throws AuthenticationException
   * @throws ServiceException
   */  
  public boolean connect(JDCConnection oConn, String sUser, String sWorkArea, DistributedCachePeer oCache)
  	throws IOException, SQLException, MalformedURLException, AuthenticationException, ServiceException {
	
	if (DebugFile.trace) {
	  DebugFile.writeln("Begin GCalendarSynchronizer.connect([JDCConnection], "+sUser+","+sWorkArea+"[DistributedCachePeer])");
	  DebugFile.incIdent();
	}
	
	PasswordRecord oPrec = null;
	
	sGuUser = sUser;
	sGuWorkArea = sWorkArea;

	boolean bCalendarFound = false;
	
	PreparedStatement oStmt;
	ResultSet oRSet;
	
	iIdDomain = oWrkA.get(sWorkArea);
	if (null==iIdDomain) {
	  oStmt = oConn.prepareStatement("SELECT "+DB.id_domain+" FROM "+DB.k_workareas+" WHERE "+DB.gu_workarea+"=?");
	  oStmt.setString(1, sWorkArea);
	  oRSet = oStmt.executeQuery();
	  if (oRSet.next()) {
	  	iIdDomain = new Integer(oRSet.getInt(1));
	  	oWrkA.put(sWorkArea, iIdDomain);
	  }
	  oRSet.close();
	  oStmt.close();
	}
	if (null==iIdDomain) throw new SQLException("WorkArea "+sWorkArea+" not found");
	
	if (oCache!=null) {
	  oPrec = (PasswordRecord) oCache.get(sUser+"[gmail]");
	}
	
	try {
		  if (null==oPrec) {
		    oPrec = new PasswordRecord();
		    if (DebugFile.trace)
		      DebugFile.writeln("JDCConnection.prepareStatement(SELECT "+DB.gu_pwd+" FROM "+DB.k_user_pwd+" WHERE "+DB.gu_user+"='"+sUser+"' AND "+DB.id_pwd+"='gmail'");
		    oStmt = oConn.prepareStatement("SELECT "+DB.gu_pwd+" FROM "+DB.k_user_pwd+" WHERE "+DB.gu_user+"=? AND "+DB.id_pwd+"='gmail'",
		    								 ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
		    oStmt.setString(1, sUser);
		    oRSet = oStmt.executeQuery();
		    if (oRSet.next())
		    	oPrec.load(oConn, oRSet.getString(1));
		    oRSet.close();
		    oStmt.close();
		    String sCal = (String) oPrec.getValueOf("cal");
		    if (sCal==null)
		    	oPrec = new PasswordRecord();
		    else if (sCal.length()==0)
		    	oPrec = new PasswordRecord();
		    if (oCache!=null) oCache.put(sUser+"[gmail]", oPrec);
		  } else {
		    if (DebugFile.trace)
		      DebugFile.writeln("cache hit for GMail account of user "+sUser);
		  }

	   
		  if (oPrec.lines().size()>0) {
		    if (DebugFile.trace)
		      DebugFile.writeln("CalendarService.setUserCredentials("+oPrec.getValueOf("user")+", ...)");
	    	  oCalSrv.setUserCredentials((String) oPrec.getValueOf("user"), (String) oPrec.getValueOf("pwd"));
		    URL feedUrl = new URL("https://www.google.com/calendar/feeds/default/allcalendars/full");
		    CalendarFeed oFeed = oCalSrv.getFeed(feedUrl, CalendarFeed.class);
		    for (int i = 0; i < oFeed.getEntries().size() && !bCalendarFound; i++) {
	    	    CalendarEntry oCalendar = oFeed.getEntries().get(i);
	    	    bCalendarFound = oCalendar.getTitle().getPlainText().equals(oPrec.getValueOf("cal"));
		    } // next
		    if (bCalendarFound) {
		    	sEMail = (String) oPrec.getValueOf("user");
		    } else {
		      if (oCache!=null) {
		        oCache.expire(sUser+"[gmail]");
		        oCache.put(sUser+"[gmail]", new PasswordRecord());	  	
		      }
		    }
		  } else {
		    if (DebugFile.trace)
		      DebugFile.writeln("PasswordRecord has not any line with a GMail account");	  
		  }
	} catch (ClassNotFoundException neverthrown) { }
	  
	if (DebugFile.trace) {
	  DebugFile.decIdent();
	  DebugFile.writeln("End GCalendarSynchronizer.connect() : "+String.valueOf(bCalendarFound));
	}

	return bCalendarFound;
  } // connect

  /**
   * <p>Read Google Calendar Event Entries for a date range</p>
   * @param oConn JDCConnection Opened JDBC database connection
   * @param dtFrom Date interval start
   * @param dtTo Date interval end
   * @return ArrayList<Meeting> The retrieved Google event entries represented as hipergate Meeting objects.
   * @throws IOException
   * @throws SQLException
   * @throws MalformedURLException
   * @throws ServiceException
   */  

  public ArrayList<Meeting> readMeetingsFromGoogle(JDCConnection oConn, Date dtFrom, Date dtTo)
  	throws IOException, SQLException, MalformedURLException, ServiceException {

	if (DebugFile.trace) {
	  DebugFile.writeln("Begin GCalendarSynchronizer.readMeetingsFromGoogle([oConn]"+dtFrom+","+dtTo);
	  DebugFile.incIdent();
	}
    
    ArrayList<Meeting> aMeetings = new ArrayList<Meeting>();
    
    URL feedUrl = new URL("https://www.google.com/calendar/feeds/default/private/full");
	
    CalendarQuery myQuery = new CalendarQuery(feedUrl);
    myQuery.setMinimumStartTime(new DateTime(dtFrom));
    myQuery.setMaximumStartTime(new DateTime(dtTo));

	CalendarEventFeed oFeed = oCalSrv.query(myQuery, CalendarEventFeed.class);  	

	if (DebugFile.trace)
	  DebugFile.writeln(String.valueOf(oFeed.getEntries().size())+" events found at Google calendar");

	ResultSet oRSet;
    PreparedStatement oStmm = oConn.prepareStatement("SELECT "+DB.gu_meeting+" FROM "+DB.k_meetings+" WHERE "+DB.gu_workarea+"=? AND "+DB.id_icalendar+"=?", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    PreparedStatement oStmf = oConn.prepareStatement("SELECT "+DB.gu_fellow+" FROM "+DB.k_fellows+" WHERE "+DB.gu_workarea+"=? AND "+DB.tx_email+"=?", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

	for (int i = 0; i < oFeed.getEntries().size(); i++) {
  	  CalendarEventEntry oEvnt = oFeed.getEntries().get(i);
  	  List<When> oTimes = oEvnt.getTimes();

	  if (DebugFile.trace)
	    DebugFile.writeln("Synchronizing event "+oEvnt.getIcalUID()+" which occurs "+String.valueOf(oTimes.size())+" times");

  	  if (oTimes.size()>0) {
  	  	Meeting oMeet = new Meeting();
  	    boolean bAlreadyExists = false;
  	    
  	    oStmm.setString(1, sGuWorkArea);
  	    oStmm.setString(2, oEvnt.getIcalUID());
  	    oRSet = oStmm.executeQuery();
  	    if (oRSet.next())
  	      bAlreadyExists = oMeet.load(oConn, oRSet.getString(1));
  	    else
  	      oMeet.put(DB.gu_meeting, Gadgets.generateUUID());
		oRSet.close();

	    if (DebugFile.trace)
	      DebugFile.writeln(bAlreadyExists ? "event already exists at local calendar" : "event does not exist at local calendar");
		  	    
  	    oMeet.put(DB.id_domain, iIdDomain);
  	    oMeet.put(DB.gu_workarea, sGuWorkArea);
  	    oMeet.put(DB.gu_fellow, sGuUser);
  	    oMeet.put(DB.gu_writer, sGuUser);
  	    oMeet.put(DB.bo_private, (short) (oEvnt.getVisibility().getValue().equals(Visibility.PRIVATE_VALUE) || oEvnt.getVisibility().getValue().equals(Visibility.CONFIDENTIAL_VALUE) ? 1 : 0));
  	    oMeet.put(DB.id_icalendar, oEvnt.getIcalUID());
  	    oMeet.put(DB.dt_start, new Date(oTimes.get(0).getStartTime().getValue()));
  	    oMeet.put(DB.dt_end, new Date(oTimes.get(0).getEndTime().getValue()));
  	    if (oEvnt.getTitle()!=null) oMeet.put(DB.tx_meeting, oEvnt.getTitle().getPlainText());
  	    if (oEvnt.getPlainTextContent()!=null) oMeet.put(DB.de_meeting, oEvnt.getPlainTextContent());
		oMeet.store(oConn);
		
		DBSubset oFlws = bAlreadyExists ? oMeet.getFellows(oConn) : new DBSubset(DB.k_fellows, "'','','','',''", null, 1);
		
		for (EventWho oEwho : oEvnt.getParticipants()) {
		  String sEMail = oEwho.getEmail();
		  if (sEMail!=null) {
	        if (DebugFile.trace) DebugFile.writeln("adding attendant "+sEMail);
		  	int iFlw = oFlws.find(4, sEMail);
		  	if (iFlw<0) {
		  	  oStmf.setString(1, sGuWorkArea);
		  	  oStmf.setString(2, sEMail);
		      oRSet = oStmf.executeQuery();
		      if (oRSet.next()) {
		        oMeet.addAttendant(oConn, oRSet.getString(1));
		      } // fi
		      oRSet.close();
		  	} // fi
		  } // fi
		} // next
		
		if (DebugFile.trace) {
  	      DebugFile.writeln("iCalUID="+oMeet.getStringNull(DB.id_icalendar,""));
  	      DebugFile.writeln("Title="+oMeet.getStringNull(DB.tx_meeting,""));
  	      DebugFile.writeln("Summary="+oMeet.getStringNull(DB.de_meeting,""));
  	      DebugFile.writeln("Start="+oMeet.getDateTime24(DB.dt_start));
  	      DebugFile.writeln("End="+oMeet.getDateTime24(DB.dt_end));
		}
		
  	    aMeetings.add(oMeet);
  	  }  	  
	} // next
	oStmf.close();
	oStmm.close();

	if (DebugFile.trace) {
	  DebugFile.decIdent();
	  DebugFile.writeln("End GCalendarSynchronizer.readMeetingsFromGoogle() : "+String.valueOf(aMeetings.size()));
	}
	
	return aMeetings;
  } // readMeetingsFromGoogle

  /**
   * <p>Create or update an hipergate Meeting as a Google Calendar Event Entry</p>
   * @param oConn JDCConnection Opened JDBC database connection
   * @param Meeting Fully loaded instance of hipergate Meeting to be written to Google Calendar
   * @throws IOException
   * @throws SQLException
   * @throws MalformedURLException
   * @throws ServiceException
   */  

  public void writeMeetingToGoogle(JDCConnection oConn, Meeting oMeet)
  	throws IOException, SQLException, MalformedURLException, ServiceException {

	URL postUrl = new URL("https://www.google.com/calendar/feeds/"+sEMail+"/private/full");

	CalendarEventEntry oEvnt;

	if (oMeet.isNull(DB.id_icalendar)) {
	  oEvnt = new CalendarEventEntry();
	  oEvnt.setIcalUID(oMeet.getString(DB.gu_meeting)+"@hipergate.org");
	  oEvnt.setTitle(new PlainTextConstruct(oMeet.getStringNull(DB.tx_meeting,"")));
      oEvnt.setContent(new PlainTextConstruct(oMeet.getStringNull(DB.de_meeting,"")));
	  
	  When oTimes = new When();
	  String sTimeZone = DBCommand.queryStr(oConn, "SELECT "+DB.tx_timezone+" FROM "+DB.k_fellows+" WHERE "+DB.gu_fellow+"='"+oMeet.getString(DB.gu_fellow)+"'");
      if (null==sTimeZone) {
        oTimes.setStartTime(new DateTime(oMeet.getDate(DB.dt_start)));
	    oTimes.setEndTime(new DateTime(oMeet.getDate(DB.dt_end)));
      } else {
        int lSign = sTimeZone.charAt(0)=='+' ? 1 : -1;
        String[] aTimeZone = Gadgets.split2(sTimeZone.substring(1),':');
        SimpleTimeZone oTmz = new SimpleTimeZone(lSign*(Integer.parseInt(aTimeZone[0])*3600000+Integer.parseInt(aTimeZone[1])*60000), sTimeZone);
        oTimes.setStartTime(new DateTime(oMeet.getDate(DB.dt_start),oTmz));
	    oTimes.setEndTime(new DateTime(oMeet.getDate(DB.dt_end),oTmz));
      }
	  oEvnt.addTime(oTimes);
	  DBSubset oFlws = oMeet.getFellows(oConn);
	  for (int f=0; f<oFlws.getRowCount(); f++) {
	    if (!oFlws.isNull(4,f)) {
	      EventWho oEwho = new EventWho();
	      oEwho.setEmail(oFlws.getString(4,f));
	      oEvnt.addParticipant(oEwho);
	    } // fi
	  } // next
	  oEvnt = oCalSrv.insert(postUrl, oEvnt);

	  if (!oMeet.isNull(DB.bo_private))
	    oEvnt.getVisibility().setValue(oMeet.getShort(DB.bo_private)==0 ? Visibility.PUBLIC_VALUE : Visibility.PRIVATE_VALUE);	  	
	  
	  DBCommand.executeUpdate(oConn, "UPDATE "+DB.k_meetings+" SET "+DB.id_icalendar+"='"+oMeet.getString(DB.gu_meeting)+"@hipergate.org"+"' WHERE "+DB.gu_meeting+"='"+oMeet.getString(DB.gu_meeting)+"'");
	} else {
      URL feedUrl = new URL("https://www.google.com/calendar/feeds/default/private/full");
      CalendarQuery myQuery = new CalendarQuery(feedUrl);
      myQuery.setMinimumStartTime(new DateTime(oMeet.getDate(DB.dt_start)));
      myQuery.setMaximumStartTime(new DateTime(oMeet.getDate(DB.dt_end)));

	  CalendarEventFeed oFeed = oCalSrv.query(myQuery, CalendarEventFeed.class);  	
	  for (int i = 0; i < oFeed.getEntries().size(); i++) {
  	    oEvnt = oFeed.getEntries().get(i);
	    if (oEvnt.getIcalUID().equals(oMeet.getString(DB.id_icalendar))) {
	      oEvnt.setTitle(new PlainTextConstruct(oMeet.getStringNull(DB.tx_meeting,"")));
          oEvnt.setContent(new PlainTextConstruct(oMeet.getStringNull(DB.de_meeting,"")));
	  	  if (oMeet.isNull(DB.bo_private))
	        oEvnt.getVisibility().setValue(Visibility.DEFAULT_VALUE);
	      else
	        oEvnt.getVisibility().setValue(oMeet.getShort(DB.bo_private)==0 ? Visibility.PUBLIC_VALUE : Visibility.PRIVATE_VALUE);	  	
	      When oTimes = new When();
          oTimes.setStartTime(new DateTime(oMeet.getDate(DB.dt_start)));
	      oTimes.setEndTime(new DateTime(oMeet.getDate(DB.dt_end)));
	      oEvnt.addTime(oTimes);
	      DBSubset oFlws = oMeet.getFellows(oConn);
	      for (int f=0; f<oFlws.getRowCount(); f++) {
	        if (!oFlws.isNull(4,f)) {
	          EventWho oEwho = new EventWho();
	          oEwho.setEmail(oFlws.getString(4,f));
	          oEvnt.addParticipant(oEwho);
	        } // fi
	      } // next
	      if (DebugFile.trace)
	        DebugFile.writeln("CalendarService.update("+new URL(oEvnt.getEditLink().getHref()).toString()+","+oEvnt.getIcalUID());
	      oCalSrv.update(new URL(oEvnt.getEditLink().getHref()), oEvnt);
	      break;
	    } // fi
	  } // next	      
	} // fi
  } // writeMeetingToGoogle

  /**
   * <p>Delete Google Calendar Event Entry which iCalendar UID is the same as the one of the given Meeting</p>
   * @param oConn JDCConnection Opened JDBC database connection
   * @param Meeting Fully loaded instance of hipergate Meeting to be deleted from Google Calendar
   * @throws IOException
   * @throws SQLException
   * @throws MalformedURLException
   * @throws ServiceException
   */  

  public void deleteMeetingFromGoogle(JDCConnection oConn, Meeting oMeet)
  	throws IOException, SQLException, MalformedURLException, ServiceException {

	if (DebugFile.trace) {
	  DebugFile.writeln("Begin GCalendarSynchronizer.deleteMeetingFromGoogle([JDCConnection], "+oMeet.getStringNull(DB.gu_meeting,"")+")");
	  DebugFile.incIdent();
	}

	if (!oMeet.isNull(DB.id_icalendar)) {
      URL feedUrl = new URL("https://www.google.com/calendar/feeds/default/private/full");
	  URL postUrl = new URL("https://www.google.com/calendar/feeds/"+sEMail+"/private/full");

      CalendarQuery myQuery = new CalendarQuery(feedUrl);

	  String sTimeZone = DBCommand.queryStr(oConn, "SELECT "+DB.tx_timezone+" FROM "+DB.k_fellows+" WHERE "+DB.gu_fellow+"='"+oMeet.getString(DB.gu_fellow)+"'");
      if (null==sTimeZone) {
        myQuery.setMinimumStartTime(new DateTime(oMeet.getDate(DB.dt_start)));
        myQuery.setMaximumStartTime(new DateTime(oMeet.getDate(DB.dt_end)));
	    if (DebugFile.trace) DebugFile.writeln("CalendarQuery.query("+feedUrl.toString()+","+oMeet.getDateTime24(DB.dt_start)+","+oMeet.getDateTime24(DB.dt_end)+", without time zone)");
      } else {
        int lSign = sTimeZone.charAt(0)=='+' ? 1 : -1;
        String[] aTimeZone = Gadgets.split2(sTimeZone.substring(1),':');
        SimpleTimeZone oTmz = new SimpleTimeZone(lSign*(Integer.parseInt(aTimeZone[0])*3600000+Integer.parseInt(aTimeZone[1])*60000), sTimeZone);
        myQuery.setMinimumStartTime(new DateTime(oMeet.getDate(DB.dt_start),oTmz));
        myQuery.setMaximumStartTime(new DateTime(oMeet.getDate(DB.dt_end),oTmz));
	    if (DebugFile.trace) DebugFile.writeln("CalendarQuery.query("+feedUrl.toString()+","+oMeet.getDateTime24(DB.dt_start)+","+oMeet.getDateTime24(DB.dt_end)+", GMT "+oTmz.getID()+")");
      }

	  CalendarEventFeed oFeed = oCalSrv.query(myQuery, CalendarEventFeed.class);  	
	  for (int i = 0; i < oFeed.getEntries().size(); i++) {
  	    CalendarEventEntry oEvnt = oFeed.getEntries().get(i);
	    if (oEvnt.getIcalUID().equals(oMeet.getString(DB.id_icalendar))) {
	      if (DebugFile.trace)
	        DebugFile.writeln("CalendarService.update("+new URL(oEvnt.getEditLink().getHref()).toString()+","+oEvnt.getIcalUID());
	      oEvnt = oCalSrv.update(new URL(oEvnt.getEditLink().getHref()), oEvnt);
	      if (DebugFile.trace) DebugFile.writeln("CalendarService.delete("+new URL(oEvnt.getEditLink().getHref()).toString()+",*)");
		  oCalSrv.delete(new URL(oEvnt.getEditLink().getHref()),"*");
	      break;
	    } // fi
	  } // next	      
	} else {
	  if (DebugFile.trace) DebugFile.writeln("meeting does not have an iCalendar unique identifier");
	}

	if (DebugFile.trace) {
	  DebugFile.decIdent();
	  DebugFile.writeln("End GCalendarSynchronizer.deleteMeetingFromGoogle()");
	}

  } // deleteMeetingFromGoogle

}
