/*
  Copyright (C) 2003  Know Gate S.L. All rights reserved.
                      C/O�a, 107 1�2 28050 Madrid (Spain)

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

package com.knowgate.misc;

import java.util.Date;
import java.util.GregorianCalendar;

import com.knowgate.debug.DebugFile;

/**
 * <p>Calendar localization functions</p>
 * @author Sergio Montoro Ten
 * @version 2.0
 */

public class Calendar {

  private static String WeekDayNamesES[] = { null, "domingo", "lunes", "martes", "mi�rcoles", "jueves", "viernes", "s�bado" };
  private static String WeekDayNamesEN[] = { null, "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday" };
  private static String WeekDayNamesIT[] = { null, "Domenica", "Luned�", "Marted�", "Mercoled�", "Gioved�", "Venerd�", "Sabato" };
  private static String WeekDayNamesFR[] = { null, "Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi" };
  private static String WeekDayNamesDE[] = { null, "Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag" };
  private static String WeekDayNamesPT[] = { null, "Domingo", "Segunda-feira", "Ter�a-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "S�bado" };
  private static String WeekDayNamesRU[] = { null, "???????????", "???????????", "???????", "Wednesday", "???????", "???????", "???????" };
  private static String WeekDayNamesCN[] = { null, "", "???????????", "???????", "Wednesday", "???????", "???????", "???????" };
  private static String WeekDayNamesNO[] = { null, "s�ndag", "mandag", "tirsdag", "onsdag", "torsdag", "fredag", "l�rdag" };

  private static String MonthNamesES[] = { "Enero","Febrero","Marzo","Abril","Mayo","Junio","Julio","Agosto","Septiembre","Octubre","Noviembre","Diciembre" };
  private static String MonthNamesEN[] = { "January","February","March","April","May","June","July","August","September","October","November","December" };
  private static String MonthNamesIT[] = { "Gennaio","Febbraio","Marzo","Aprile","Maggio","Giugno","Luglio","Agosto","Settembre","Ottobre","Novembre","Dicembre" };
  private static String MonthNamesFR[] = { "Janvier","F�vrier","Mars","Avril","Mai","Juin","Juillet","Ao�t","Septembre","Octobre","Novembre","D�cembre" };
  private static String MonthNamesDE[] = { "Januar","Februar","M�rz","April","Mai","Juni","Juli","August","September","Oktober","November","Dezember" };
  private static String MonthNamesPT[] = { "Janeiro","Fevereiro","Mar�o","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro" };
  private static String MonthNamesRU[] = { "??????","???????","?????","??????","???","????","????","???????","????????","???????","??????","???????" };
  private static String MonthNamesNO[] = { "januar","februar","mars","april","mai","juni","juli","august","september","oktober","november","desember" };

  private static String MonthNamesRFC[] = { "Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec" };

  //-----------------------------------------------------------

  /**
   * Get translated week day name
   * @param MyWeekDay [1=Sunday .. 7=Saturday]
   * @param sLangId 2 characters language identifier (currently only { "en","es","it","fr","de" and "pt" } are supported)
   * @return Week day name
   */
  public static String WeekDayName(int MyWeekDay, String sLangId) {

    if (DebugFile.trace) {
      DebugFile.writeln("Begin WeekDayName(" + String.valueOf(MyWeekDay) + "," + sLangId + ")");
      DebugFile.incIdent();
    }

    String sRetVal;

    if (MyWeekDay<1 || MyWeekDay>7)
      throw new java.lang.IllegalArgumentException("Calendar.WeekDayName 1st parameter (MyWeekDay) is " + String.valueOf(MyWeekDay) + " but must be in the range [1..7]");
    else {
      if (null==sLangId)
        throw new java.lang.IllegalArgumentException("Calendar.WeekDayName 2nd parameter (Language Id.) is null but must be one of {es,en}");
      else if (sLangId.equalsIgnoreCase("es"))
        sRetVal = WeekDayNamesES[MyWeekDay];
      else if (sLangId.equalsIgnoreCase("en"))
        sRetVal = WeekDayNamesEN[MyWeekDay];
      else if (sLangId.equalsIgnoreCase("fr"))
        sRetVal = WeekDayNamesFR[MyWeekDay];
      else if (sLangId.equalsIgnoreCase("de"))
        sRetVal = WeekDayNamesDE[MyWeekDay];
      else if (sLangId.equalsIgnoreCase("it"))
        sRetVal = WeekDayNamesIT[MyWeekDay];
      else if (sLangId.equalsIgnoreCase("pt"))
        sRetVal = WeekDayNamesPT[MyWeekDay];
      else if (sLangId.equalsIgnoreCase("ru"))
        sRetVal = WeekDayNamesRU[MyWeekDay];
      else if (sLangId.equalsIgnoreCase("no"))
        sRetVal = WeekDayNamesNO[MyWeekDay];
      else
        throw new java.lang.IllegalArgumentException("Calendar.WeekDayName 2nd parameter (Language Id.) must be one of {es,en}");
    }

    if (DebugFile.trace) {
      DebugFile.decIdent();
      DebugFile.writeln("End WeekDayName() : " + sRetVal);
    }

    return sRetVal;
  } // WeekDay

  //-----------------------------------------------------------

  /**
   * Get translated month name
   * @param MyMonth [0=January .. 11=December]
   * @param sLangId sLangId 2 characters language identifier (currently only  { "en","es","it","fr","de" and "pt" } are supported)
   * @return Month Name
   * @throws IllegalArgumentException if sLangId is not one of {es, en, fr, it, de, pt}
   */
  public static String MonthName(int MyMonth, String sLangId)
  	throws IllegalArgumentException {

    if (DebugFile.trace) {
      DebugFile.writeln("Begin MonthName(" + String.valueOf(MyMonth) + "," + sLangId + ")");
      DebugFile.incIdent();
    }

    String sRetVal;

    if (MyMonth<0 || MyMonth>11)
      throw new java.lang.IllegalArgumentException("Calendar.MonthName 1st parameter (MyMonth) is " + String.valueOf(MyMonth) + " but must be in the range [0..11]");
    else {
      if (null==sLangId)
        throw new java.lang.IllegalArgumentException("Calendar.MonthName 2nd parameter (Language Id.) is null but must be one of {es,en}");
      else if (sLangId.equalsIgnoreCase("es"))
        sRetVal = MonthNamesES[MyMonth];
      else if (sLangId.equalsIgnoreCase("en"))
        sRetVal = MonthNamesEN[MyMonth];
      else if (sLangId.equalsIgnoreCase("fr"))
        sRetVal = MonthNamesFR[MyMonth];
      else if (sLangId.equalsIgnoreCase("it"))
        sRetVal = MonthNamesIT[MyMonth];
      else if (sLangId.equalsIgnoreCase("de"))
        sRetVal = MonthNamesDE[MyMonth];
      else if (sLangId.equalsIgnoreCase("pt"))
        sRetVal = MonthNamesPT[MyMonth];
      else if (sLangId.equalsIgnoreCase("no"))
        sRetVal = MonthNamesNO[MyMonth];
      else
        throw new java.lang.IllegalArgumentException("Calendar.WeekDayName 2nd parameter (Language Id.) must be one of {es,en}");
    }

    if (DebugFile.trace) {
      DebugFile.decIdent();
      DebugFile.writeln("End MonthName() : " + sRetVal);
    }

    return sRetVal;
  } // MonthName

  //-----------------------------------------------------------

  /**
   * Get Month Last Day
   * @param MyMonth [0=January .. 11=December]
   * @param MyYear 4 digits year
   * @return the last day of the month. Takes into account leap years
   */
  public static int LastDay(int MyMonth, int MyYear) {

    if (MyMonth<0 || MyMonth>11)
      throw new java.lang.IllegalArgumentException("Calendar.LastDay 1st parameter (MyMonth) is " + String.valueOf(MyMonth) + " but must be in the range [0..11]");

    if (MyYear<1000 || MyYear>9999)
      throw new java.lang.IllegalArgumentException("Calendar.LastDay 2nd parameter (MyYear) is " + String.valueOf(MyYear) + " but must be in the range [1000..9999]");

    switch(MyMonth) {
      case 0:
      case 2:
      case 4:
      case 6:
      case 7:
      case 9:
      case 11:
        return 31;
      case 3:
      case 5:
      case 8:
      case 10:
        return 30;
      case 1:
        return ( (MyYear%400==0) || ((MyYear%4==0) && (MyYear%100!=0)) ) ? 29 : 28;
    } // end switch()
    return 0;
  } // LastDay()

  //-----------------------------------------------------------

  public static int DaysBetween(Date dt1st, Date dt2nd) {
	return (int) Math.round(((double) (dt2nd.getTime() - dt1st.getTime())) / 86400000d); 
  } // DaysBetween

  //-----------------------------------------------------------

  public static int DaysBetween(GregorianCalendar dt1st, GregorianCalendar dt2nd) {
	return (int) Math.round(((double) (dt2nd.getTime().getTime() - dt1st.getTime().getTime())) / 86400000d); 
  } // DaysBetween

  //-----------------------------------------------------------

}