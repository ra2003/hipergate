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

package com.knowgate.scheduler;


import java.lang.Thread;
import java.util.Date;
import java.util.LinkedList;
import java.util.ListIterator;

import java.sql.SQLException;

import com.knowgate.jdc.JDCConnection;
import com.knowgate.dataobjs.DB;
import com.knowgate.debug.DebugFile;


/**
 * <p>Scheduled Job Worker Thread</p>
 * @author Sergio Montoro Ten
 * @version 1.0
 */

public class WorkerThread extends Thread {

  private String sLastError;
  private Job  oJob; // Current Job
  private Atom oAtm; // Atom being processed
  private long lRunningTime;
  private int delay = 1; // Thread sleeps n miliseconds on each loop
  private AtomConsumer oConsumer;
  private WorkerThreadPool oPool;
  private LinkedList oCallbacks;
  private int iCallbacks;
  private boolean bContinue;

  // ----------------------------------------------------------

  /**
   * Create WorkerThread
   * @param oThreadPool
   * @param oAtomConsumer
   */

  public WorkerThread(WorkerThreadPool oThreadPool, AtomConsumer oAtomConsumer) {
    oConsumer = oAtomConsumer;
    oPool = oThreadPool;
    oCallbacks = new LinkedList();
    iCallbacks = 0;
    oJob = null;
    sLastError = "";
    lRunningTime = 0;
  }

  // ----------------------------------------------------------

  public int getDelayMS() {
    return delay;
  }

  // ----------------------------------------------------------

  public void getDelayMS(int iMiliseconds) {
    delay=iMiliseconds;
  }

  // ----------------------------------------------------------

  public long getRunningTimeMS() {
    return lRunningTime;
  }

  // ----------------------------------------------------------

  public void setConsumer (AtomConsumer oAtomConsumer) {
    oConsumer = oAtomConsumer;
  }

  // ----------------------------------------------------------

  /**
   * Get Environment property from hipergate.cnf
   * @param sKey Property Name
   * @return Property Value or <b>null</b> if not found
   */
  public String getProperty(String sKey) {
    return oPool.getProperty(sKey);
  }

  // ---------------------------------------------------------------------------

  public Atom activeAtom() {
    return oAtm;
  }

  // ---------------------------------------------------------------------------

  public Job activeJob() {
    return oJob;
  }

  // ---------------------------------------------------------------------------

  public String lastError() {
    return sLastError;
  }

  // ---------------------------------------------------------------------------

  /**
   * Register a thread callback object
   * @param oNewCallback WorkerThreadCallback subclass instance
   * @throws IllegalArgumentException If a callback with same name has oNewCallback was already registered
   */
  public void registerCallback(WorkerThreadCallback oNewCallback)
    throws IllegalArgumentException {

    WorkerThreadCallback oCallback;
    ListIterator oIter = oCallbacks.listIterator();

    while (oIter.hasNext()) {
      oCallback = (WorkerThreadCallback) oIter.next();

      if (oCallback.name().equals(oNewCallback.name())) {
        throw new IllegalArgumentException("Callback " + oNewCallback.name() + " is already registered");
      } // fi
    } // wend

    oCallbacks.addLast(oNewCallback);
    iCallbacks++;
  } // registerCallback

  // ---------------------------------------------------------------------------

  /**
   * Unregister a thread callback object
   * @param sCallbackName Name of callback to be unregistered
   * @return <b>true</b> if a callback with such name was found and unregistered,
   * <b>false</b> otherwise
   */
  public boolean unregisterCallback(String sCallbackName) {
    WorkerThreadCallback oCallback;
    ListIterator oIter = oCallbacks.listIterator();

    while (oIter.hasNext()) {
      oCallback = (WorkerThreadCallback) oIter.next();

      if (oCallback.name().equals(sCallbackName)) {
        oIter.remove();
        iCallbacks--;
        return true;
      } // fi
    } // wend

    return false;
  } // unregisterCallback

  // ---------------------------------------------------------------------------

  private void callBack(int iOpCode, String sMessage, Exception oXcpt, Object oParam) {
    WorkerThreadCallback oCallback;
    ListIterator oIter = oCallbacks.listIterator();

    while (oIter.hasNext()) {
      oCallback = (WorkerThreadCallback) oIter.next();
      oCallback.call(getName(), iOpCode, sMessage, oXcpt, oParam);
    } // wend

  }

  // ---------------------------------------------------------------------------

  /**
   * <p>Process atoms obtained throught AtomConsumer</p>
   * Each worker WorkerThread will enter an endless loop until the queue is empty
   * or an interrupt signal is received.<br>
   * If an exception is thrown while creating of processing atoms the workerthread
   * will be aborted.
   */
  public void run() {
    String sJob = ""; // Current Job Unique Id.
    JDCConnection oConsumerConnection = null;

    if (DebugFile.trace) {
       DebugFile.writeln("Begin WorkerThread.run()");
       DebugFile.incIdent();
       DebugFile.writeln("thread=" + getName());
     }

    bContinue = true;

    sLastError = "";

    while (bContinue) {

      try {
        if (delay>0) sleep(delay);

        long lStartRun = new Date().getTime();

        if (DebugFile.trace) DebugFile.writeln(getName() + " getting next atom...");

        oAtm = oConsumer.next();

        if (oAtm==null) {
          // No more atoms to consume
          if (DebugFile.trace) DebugFile.writeln(getName() + " no more atoms.");

          if (iCallbacks>0) callBack (WorkerThreadCallback.WT_ATOMCONSUMER_NOMORE, "Thread " + getName() + " no more Atoms", null, oConsumer);

          break;
        }

        if (iCallbacks>0) callBack (WorkerThreadCallback.WT_ATOM_GET, "Thread " + getName() + " got Atom " + String.valueOf(oAtm.getInt(DB.pg_atom)), null, oAtm);

        oConsumerConnection = oConsumer.getConnection();

        if (DebugFile.trace) DebugFile.writeln(getName() + " AtomConsumer.getConnection() : " + (oConsumerConnection!=null ? "[Conenction]" : "null"));

        // ***********************************
        // Instantiate the proper Job subclass

        if (!sJob.equals(oAtm.getString(DB.gu_job))) {

          // The Job is only re-loaded if it is different from the previous job at this thread
          // this is a Job instance reuse policy for better performance.

          sJob = oAtm.getString(DB.gu_job);

          try {
            // Dynamically instantiate the job subclass specified at k_lu_job_commands table
            oJob = Job.instantiate(oConsumerConnection, sJob, oPool.getProperties());

            if (iCallbacks>0) callBack(WorkerThreadCallback.WT_JOB_INSTANTIATE, "instantiate job " + sJob + " command " + oJob.getString(DB.id_command), null, oJob);
          }
          catch (ClassNotFoundException e) {
            sJob = "";
            oJob = null;
            sLastError = "Job.instantiate(" + sJob + ") ClassNotFoundException " + e.getMessage();

            if (DebugFile.trace) DebugFile.writeln(getName() + " " + sLastError);

            if (iCallbacks>0) callBack(-1, sLastError, e, null);

            bContinue = false;
          }
          catch (IllegalAccessException e) {
            sJob = "";
            oJob = null;
            sLastError = "Job.instantiate(" + sJob + ") IllegalAccessException " + e.getMessage();

            if (DebugFile.trace) DebugFile.writeln(getName() + " " + sLastError);

            if (iCallbacks>0) callBack(-1, sLastError, e, null);

            bContinue = false;
          }
          catch (InstantiationException e) {
            sJob = "";
            oJob = null;
            sLastError = "Job.instantiate(" + sJob + ") InstantiationException " + e.getMessage();

            if (DebugFile.trace) DebugFile.writeln(getName() + " " + sLastError);

            if (iCallbacks>0) callBack(-1, sLastError, e, null);

            bContinue = false;
          }
          catch (SQLException e) {
            sJob = "";
            oJob = null;
            sLastError = " Job.instantiate(" + sJob + ") SQLException " + e.getMessage();

            if (DebugFile.trace) DebugFile.writeln(getName() + " " + sLastError);

            if (iCallbacks>0) callBack(-1, sLastError, e, null);

            bContinue = false;
          }
        } // fi(Previous_Job == CurrentAtom->Job)

        // ---------------------------------------------------------------------

        if (null!=oJob) {

          // -------------------------------------------------------------------
          // Actual Atom processing call here!

          oJob.process(oAtm);

          if (DebugFile.trace)
            DebugFile.writeln("Thread " + getName() + " consumed Atom " + String.valueOf(oAtm.getInt(DB.pg_atom)));

          // Move Atom register from k_job_atoms to k_job_atoms_archived
          oAtm.archive(oConsumerConnection);

          if (iCallbacks>0) callBack(WorkerThreadCallback.WT_ATOM_CONSUME, "Thread " + getName() + " consumed Atom " + String.valueOf(oAtm.getInt(DB.pg_atom)), null, oAtm);

          oAtm = null;

          if (DebugFile.trace) DebugFile.writeln("job " + oJob.getString(DB.gu_job) + " pending " + String.valueOf(oJob.pending()));

          if (oJob.pending()==0) {
            oJob.setStatus(oConsumerConnection, Job.STATUS_FINISHED);

            if (iCallbacks>0) callBack(WorkerThreadCallback.WT_JOB_FINISH, "finish", null, oJob);
          }

          // -------------------------------------------------------------------

        } // fi (oJob)
        else {
          oAtm = null;
          sLastError = "Job.instantiate(" + sJob + ") returned null";
          if (DebugFile.trace) DebugFile.writeln("ERROR: " + sLastError);

          if (iCallbacks>0) callBack(-1, sLastError, new NullPointerException("Job.instantiate(" + sJob + ")"), null);

          bContinue = false;
        }
        oConsumerConnection = null;
        lRunningTime += new Date().getTime()-lStartRun;
      }
      catch (Exception e) {

        if (DebugFile.trace)
          DebugFile.writeln(getName() + " " + e.getClass().getName() + " " + e.getMessage());

        if (null!=oJob) {
          sLastError = e.getClass().getName() + ", job " + oJob.getString(DB.gu_job) + " ";
          if (null!=oAtm) {
            sLastError = "atom " + String.valueOf(oAtm.getInt(DB.pg_atom)) + " ";
            if (null!=oConsumerConnection) {
              try {
                oAtm.setStatus(oConsumerConnection, Atom.STATUS_INTERRUPTED, e.getClass().getName() + " " + e.getMessage());
              } catch (SQLException sqle) {
                if (DebugFile.trace) DebugFile.writeln("Atom.setStatus() SQLException " + sqle.getMessage());
              }
            }
          }
          sLastError += e.getMessage();

          oJob.log(getName() + " " + e.getClass().getName() + ", job " + oJob.getString(DB.gu_job) + " ");
          if (null!=oAtm) oJob.log("atom " + String.valueOf(oAtm.getInt(DB.pg_atom)) + " ");
          oJob.log(e.getMessage() + "\n");
        } // fi (oJob)
        else
          sLastError = e.getClass().getName() + " " + e.getMessage();

        if (iCallbacks>0) callBack(-1, sLastError, e, oJob);

        bContinue = false;
      }
      finally {
        sJob = "";
        oAtm = null;
      }
    } // wend

    if (oJob!=null) { oJob.free(); oJob=null; }

    if (DebugFile.trace) {
      DebugFile.decIdent();
      DebugFile.writeln("End WorkerThread.run()");
    }
  } // run

  // ---------------------------------------------------------------------------

  /**
   * <p>Halt thread execution commiting all operations in course before stopping</p>
   * If a thread is dead-locked by any reason halting it will not cause any effect.<br>
   * halt() method only sends a signals to the each WokerThread telling it that must
   * finish pending operations and stop.
   */
  public void halt() {
    bContinue = false;
  }

  // ---------------------------------------------------------------------------

} // WorkerThread
