package com.knowgate.hipergate.mobile.event.initop;

import com.knowgate.hipergate.mobile.MyOAs;
import com.knowgate.hipergate.mobile.MyPresentations;

import org.morfeo.tidmobile.context.Context;
import org.morfeo.tidmobile.server.RequestData;
import org.morfeo.tidmobile.server.ServerConstants;
import org.morfeo.tidmobile.server.flow.IActionExecutor;

/* Java code was generated by MyMobileWeb Plugin */
public class InitOpEventHandler {


  public void onInitOP(RequestData req, IActionExecutor act) throws Throwable {
	  act.executeOA(MyOAs.LIST_TODAY_MEETINGS, req);
	  act.navigate(MyPresentations.INITOP_INITPR,req);
  }

  public void lout_onclick(RequestData req, IActionExecutor act) throws Throwable {
	  act.logout(req);
  }

}