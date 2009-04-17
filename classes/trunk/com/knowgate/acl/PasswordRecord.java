/*
  Copyright (C) 2009  Know Gate S.L. All rights reserved.

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

package com.knowgate.acl;

import java.security.AccessControlException;

import java.sql.SQLException;

import java.util.ArrayList;

import com.knowgate.dataobjs.DB;
import com.knowgate.dataobjs.DBCommand;
import com.knowgate.dataobjs.DBPersist;
import com.knowgate.jdc.JDCConnection;
import com.knowgate.misc.Base64Decoder;
import com.knowgate.misc.Base64Encoder;
import com.knowgate.misc.Gadgets;
import com.knowgate.hipergate.Category;

public class PasswordRecord extends DBPersist {

  private RC4 oCrypto;
  
  private ArrayList<PasswordRecordLine> aRecordLines;
  
  public PasswordRecord(String sKey) {
    super(DB.k_user_pwd, "PasswordRecord");
  	RC4 oCrypto = new RC4(sKey);
  	
  }

  public ArrayList<PasswordRecordLine> lines() {
    return aRecordLines;
  }
  
  public boolean load (JDCConnection oConn, Object[] aPK)
  	throws SQLException, AccessControlException {
  	boolean bRetVal = super.load(oConn, aPK);
  	aRecordLines = new ArrayList<PasswordRecordLine>();
  	if (bRetVal) {
  	  if (!isNull("tx_lines")) {
  	    byte[] byLines = Base64Decoder.decodeToBytes(getString(DB.tx_lines));
  	  	String sLines = new String(oCrypto.rc4(byLines));
  	  	String[] aLines = sLines.split("\n");
  	  	if (aLines!=null) {
  	  	  if (!aLines[0].startsWith("# Password record"))
  	  	  	throw new AccessControlException("Invalid password"); 
  	  	  for (int l=1; l<aLines.length; l++) {
  	  	    String[] aLine = aLines[l].split("|");
  	  	    PasswordRecordLine oRecLin = new PasswordRecordLine();
  	  	    oRecLin.setId(aLine[0]);
  	  	    oRecLin.setLabel(aLine[2]);
  	  	    oRecLin.setValue(aLine[1].charAt(0), aLine[3]);
  	  	    aRecordLines.add(oRecLin);
  	  	  } // next
  	  	} // fi
  	  } // fi
  	} // fi  	
  	return bRetVal;
  } // load

  public boolean load (JDCConnection oConn, String sPk)
  	throws SQLException, AccessControlException {
  	return load(oConn, new Object[]{sPk});
  }
  	
  public boolean store (JDCConnection oConn)
  	throws SQLException, AccessControlException {

	boolean bIsNew = isNull(DB.gu_pwd);
	if (!bIsNew) bIsNew = !exists(oConn);

  	if (isNull(DB.gu_pwd)) {
  	  put (DB.gu_pwd, Gadgets.generateUUID());
  	}
  	
  	StringBuffer oLines = new StringBuffer("# Password record v5.0");
  	for (PasswordRecordLine rcl : aRecordLines) {
  	  oLines.append(rcl);
  	  oLines.append("\n");
  	} // next
  	  
  	replace(DB.tx_lines, Base64Encoder.encode(oCrypto.rc4(Gadgets.dechomp(oLines.toString(),'\n'))));
    boolean bRetVal = super.store(oConn);
    
    if (bIsNew) {
      String sCatName;

	  sCatName = DBCommand.queryStr(oConn, "SELECT d."+DB.nm_domain+",'_',u."+DB.tx_nickname+",'_pwds' FROM "+DB.k_domains+" d,"+DB.k_users+" u WHERE d."+DB.id_domain+"=u."+DB.id_domain+" AND u."+DB.gu_user+"='"+getString(DB.gu_user)+"'");
			
	  String sPwdsCat = DBCommand.queryStr(oConn, "SELECT "+DB.gu_category+" FROM "+DB.k_categories+" c, " + DB.k_cat_tree+ " t WHERE c."+DB.gu_category+"=t."+DB.gu_child_cat+" AND t."+DB.gu_parent_cat+" IN (SELECT "+DB.gu_category+" FROM "+DB.k_users+" WHERE "+DB.gu_user+"='"+getString(DB.gu_user)+"') AND c."+DB.nm_category+"='"+sCatName+"'");
      
      if (null!=sPwdsCat) {
        Category oPwdsCat = new Category(sPwdsCat);
        Integer oPos = DBCommand.queryMaxInt(oConn, DB.od_position, DB.k_x_cat_objs,
        									 DB.gu_category+"='"+sPwdsCat+"'");
        if (null==oPos) oPos = new Integer(1); else oPos = new Integer(oPos.intValue()+1);
        oPwdsCat.addObject(oConn, getString(DB.gu_pwd), ClassId, 0, oPos.intValue());
      } // fi
    } // fi
    return bRetVal;
  } // store

  public boolean store (JDCConnection oConn, String sGuCategory)
  	throws SQLException, AccessControlException {

	boolean bIsNew = isNull(DB.gu_pwd);
	if (!bIsNew) bIsNew = !exists(oConn);

  	if (isNull(DB.gu_pwd)) {
  	  put (DB.gu_pwd, Gadgets.generateUUID());
  	}
  	
  	StringBuffer oLines = new StringBuffer("# Password record v5.0");
  	for (PasswordRecordLine rcl : aRecordLines) {
  	  oLines.append(rcl);
  	  oLines.append("\n");
  	} // next
  	  
  	replace(DB.tx_lines, Base64Encoder.encode(oCrypto.rc4(Gadgets.dechomp(oLines.toString(),'\n'))));
    boolean bRetVal = super.store(oConn);
    
    if (bIsNew) {
      if (null!=sGuCategory) {
        Category oPwdsCat = new Category(sGuCategory);
        Integer oPos = DBCommand.queryMaxInt(oConn, DB.od_position, DB.k_x_cat_objs,
        									 DB.gu_category+"='"+sGuCategory+"'");
        if (null==oPos) oPos = new Integer(1); else oPos = new Integer(oPos.intValue()+1);
        oPwdsCat.addObject(oConn, getString(DB.gu_pwd), ClassId, 0, oPos.intValue());
      } // fi
    } // fi
    return bRetVal;
  } // store

  public boolean delete (JDCConnection oConn)
  	throws SQLException {
	return delete (oConn, getString(DB.gu_pwd));
  }

  public static boolean delete (JDCConnection oConn, String sGuPwd)
  	throws SQLException {
  
    DBCommand.executeUpdate(oConn, "DELETE FROM "+DB.k_x_cat_objs+" WHERE "+DB.gu_object+"='"+sGuPwd+"'");

    int iAffected = DBCommand.executeUpdate(oConn, "DELETE FROM "+DB.k_user_pwd+" WHERE "+DB.gu_pwd+"='"+sGuPwd+"'");

	return (iAffected>0);
  }

  // **********************************************************
  // Public Constants

  public static final short ClassId = 14;

}