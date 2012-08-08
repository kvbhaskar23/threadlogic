/**
 * Copyright (c) 2012 egross, sabha.
 * 
 * ThreadLogic - parses thread dumps and provides analysis/guidance
 * It is based on the popular TDA tool.  Thank you!
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */
/*
 * FallbackParser.java
 *
 * This file is part of ThreadLogic Thread Dump Analysis Tool.
 *
 * TDA is free software; you can redistribute it and/or modify
 * it under the terms of the Lesser GNU General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * TDA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Lesser GNU General Public License for more details.
 *
 * You should have received a copy of the Lesser GNU General Public License
 * along with TDA; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id: FallbackParser.java,v 1.47 2010-01-03 14:23:09 irockel Exp $
 */
package com.oracle.ateam.threadlogic.parsers;

import com.oracle.ateam.threadlogic.ThreadLogic;
import com.oracle.ateam.threadlogic.ThreadDumpInfo;
import com.oracle.ateam.threadlogic.categories.Category;
import com.oracle.ateam.threadlogic.categories.TableCategory;
import com.oracle.ateam.threadlogic.categories.TreeCategory;
import com.oracle.ateam.threadlogic.monitors.MonitorMap;
import com.oracle.ateam.threadlogic.utils.DateMatcher;
import com.oracle.ateam.threadlogic.utils.IconFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;

/**
 * Parses WLST & Partial Thread Dumps. Needs to be closed
 * after use (so inner stream is closed).
 * 
 * @author irockel
 */
public class FallbackParser extends AbstractDumpParser {

  private int jvmType;
  private static Pattern defaultThreadPattern = Pattern.compile(".*( WAITING| RUNNABLE| TIMED_WAITING| alive, | nid=0x).*");

  // Search keywords for jrockit or ibm specific tags in thread dumps if search for markers fail
  // Default to Hotspot for everything for else
  protected static final String JROCKIT_TAG = "jrockit";
  
  // Even if there are ibm markers, harder to parse a wlst generated IBM Thread dump with the IBMParser
  // use the Sun HotspotParser still as special case as adding the whole file/markers is difficult...
  protected static final String IBM_TAG = "com.ibm.misc.SignalDispatcher";
  

  /**
   * Creates a new instance of SunJDKParser
   */
  public FallbackParser(BufferedReader bis, Map threadStore, int lineCounter, boolean withCurrentTimeStamp,
      int startCounter, DateMatcher dm, int vmType) {
    
    super(bis, dm);
    //this.jvmType = vmType;
    this.jvmType = UNKNOWN_VM;
    this.threadStore = threadStore;
    this.withCurrentTimeStamp = withCurrentTimeStamp;
    this.lineCounter = lineCounter;
    this.counter = startCounter;
    this.lineChecker = new LineChecker();
    dm.setDefaultPattern(Pattern.compile(".*(\\d{1,2}/\\d{1,2}/\\d{1,2}\\s*\\d{1,2}:\\d{1,2}\\s*[A|P]M|\\d{4}-\\d{2}-\\d{2}\\s*\\d{1,2}:\\d{2}:\\d{2}).*"));    
    
    setUnknownVMMarkers();     
  }
  
  /**
   * check if the passed logline contains the beginning of a sun jdk thread
   * dump.
   * 
   * @param logLine
   *          the line of the logfile to test
   * @return int, 
   *          VM type if the line matches some supported JVM type.
   *          -1 if nothing can be determined
   */
  public static int checkForSupportedThreadDump(String logLine) {
    String line = logLine.trim();
    Matcher m = defaultThreadPattern.matcher(line);
    boolean matches = m.matches();
    
    if (!matches)
      return -1;
    
    String entry = (m.groupCount() == 1? m.group(1): m.group());

    if (entry.indexOf(" nid=0x") >= 0) {
        return HOTSPOT_VM;
    } else if (entry.indexOf(" alive, ") >= 0) {
        return JROCKIT_VM;
    } else {
        return UNKNOWN_VM;
    }
  }
  

  public void setUnknownVMMarkers() {
    this.lineChecker.setFullDumpPattern(".*(Thread dump for the running).*");
    this.lineChecker.setAtPattern("\\s*[^\"][a-z]*\\..*\\(.*\\)");     
    this.lineChecker.setEndOfDumpPattern(".*(\\d{1,2}/\\d{1,2}/\\d{1,2}\\s*\\d{1,2}:\\d{1,2}\\s*[A|P]M|Thread Dump at|Thread dump for the running|<EndOfDump>).*");
    
    // Handle WLST, JRockit, Sun thread labels
    this.lineChecker.setEndOfTitlePattern(".*( RUNNABLE| WAITING| BLOCKED| TIMED_WAITING).*");
    this.setJvmVendor(JVM_VENDOR_LIST[JVM_VENDOR_LIST.length - 1]);
  }

    
  /**
   * @returns true, if a class histogram was found and added during parsing.
   */
  public boolean isFoundClassHistograms() {
    return false;
  }


  /**
   * add a monitor link for monitor navigation
   * 
   * @param line
   *          containing monitor
   */
  protected String linkifyMonitor(String line) {
    return (line);
  }


  /**
   * checks for the next class histogram and adds it to the tree node passed
   * 
   * @param threadDump
   *          which tree node to add the histogram.
   */
  public boolean checkForClassHistogram(DefaultMutableTreeNode threadDump) throws IOException {
    return false;
  }  


  /**
   * generate thread info token for table view.
   * 
   * @param name
   *          the thread info.
   * @return thread tokens.
   */
  public String[] getThreadTokens(String nameEntry) {  
  
    switch(this.jvmType) {
      case(HOTSPOT_VM): 
        return getHotspotThreadTokens(nameEntry);
      case(JROCKIT_VM): 
        return getJRockitThreadTokens(nameEntry);
      default: 
        return getWLSTThreadTokens(nameEntry);
    }
  }

  /**
   * generate thread info token for table view.
   * 
   * @param name
   *          the thread info.
   * @return thread tokens.
   */
  public String[] getWLSTThreadTokens(String nameEntry) {    
  
    String[] tokens = new String[4];
    int index = nameEntry.indexOf("\"", 1);
    if (index > 1) {
      tokens[0] = nameEntry.substring(1, index);
    } else {
      tokens[0] = nameEntry.substring(1);
      return tokens;
    }

    String remainingLabel = nameEntry.substring(index + 1).trim();

    if (remainingLabel.indexOf("=") < 0) {
      if (nameEntry.contains("RUNNABLE"))
      tokens[3] = "RUNNING";
      // in WLST generated HotSpot thread dump, waiting for lock is used for general waiting
      else if (nameEntry.contains("waiting for lock"))
        tokens[3] = "waiting";
      else if (nameEntry.contains("TIMED_WAITING"))
        tokens[3] = "sleeping";
      else if (nameEntry.contains(" WAITING"))
        tokens[3] = "waiting";
    }
    return tokens;        
  }
  
  public String linkifyDeadlockInfo(String line) {
    return line;
  }  
  
  public void parseLoggcFile(InputStream loggcFileStream, DefaultMutableTreeNode root) {
  }
  
  /**
   * generate thread info token for table view.
   * 
   * @param name
   *          the thread info.
   * @return thread tokens.
   */
  public String[] getHotspotThreadTokens(String name) {
    
    String patternMask = "^.*\"([^\\\"]+)\".*tid=([^ ]+|).*nid=([^ ]+) *([^\\[]*).*";
    name = name.replace("- Thread t@", "tid=");
    
    String[] tokens = new String[] {};
   
    try {
      Pattern p = Pattern.compile(patternMask);
      Matcher m = p.matcher(name);

      m.matches();
      /*
      for (int iLoop = 1; iLoop < m.groupCount(); iLoop++) {
        System.out.println(iLoop + ": " + m.group(iLoop));
      }
       */
    
      tokens = new String[7];
      tokens[0] = m.group(1); // name
      // tokens[1] = m.group(4); // prio
      tokens[1] = m.group(3); // tid
      tokens[2] = m.group(2); // nid
      tokens[3] = m.group(4); // State

    } catch(Exception e) { 
      
      System.out.println("WARNING!! Unable to parse Thread Tokens with name:" + name);           
      //e.printStackTrace();
      
      int index = name.indexOf("\"", 1);
      if (index > 1) {
        tokens[0] = name.substring(1, index);
      } else {
        tokens[0] = name.substring(1);
        return tokens;
      }
      
      String remainingLabel = name.substring(index + 1).trim();      
    
      String[] remainingTokens = remainingLabel.replace("daemon ","").trim().split(" ");
      for(int i = 1; i < remainingTokens.length; i++) {
        if (i == 3)
          break;
        
        String label = remainingTokens[i].replaceAll(".*=", "");
        if (i == 1) // tid
          tokens[1] = label;
        else if (i == 2) // nid
          tokens[2] = label;
      } 
      
      for(int i = 3; i < remainingTokens.length; i++) {
        if (remainingTokens[i].startsWith("[0"))
          break;
        
        tokens[3] = tokens[3] + " " + remainingTokens[i];        
      }
    }
    
    return (tokens);
  }
  

  protected String[] getJRockitThreadTokens(String name) {
    String patternMask = "^.*\"([^\"]+)\".*id=([^ ]+).*tid=([^ ]+).*"
        + "prio=[^ ]+ ([^,]+,? ?[^,]+?,? ?[^,]+?,? ?[^,]+?)(, daemon)?$";
    
    String[] tokens = new String[] {};
    
    try {
      Pattern p = Pattern.compile(patternMask);
      Matcher m = p.matcher(name);

      m.matches();
      /*
      for (int iLoop = 1; iLoop < m.groupCount(); iLoop++) {
        System.out.println(iLoop + ": " + m.group(iLoop));
      }
       */

      tokens = new String[7];
      tokens[0] = m.group(1); // name
      tokens[1] = m.group(2); // tid
      tokens[2] = m.group(3); // nid
      tokens[3] = m.group(4); // State

    } catch(Exception e) { 

      System.out.println("WARNING!! Unable to parse Thread Tokens with name:" + name  );

      int index = name.indexOf("\"", 1);
      if (index > 1) {
        tokens[0] = name.substring(1, index);
      } else {
        tokens[0] = name.substring(1);
        return tokens;
      }
      
      String[] remainingTokens = name.substring(index + 1).trim().split(" ");
      for(int i = 0; i < remainingTokens.length; i++) {
        
        if (i == 3)
          break;
        
        String label = remainingTokens[i].replaceAll(".*=", "");
        if (i == 0) // nid
          tokens[2] = label;
        else if (i == 2) // tid
          tokens[1] = label;
      }
      
      tokens[3] = " " + remainingTokens[5];
    }
    return tokens;    
  }  

  /**
   * parse the next thread dump from the stream passed with the constructor.
   * 
   * @returns null if no more thread dumps were found.
   */
  public MutableTreeNode parseNext() {
    
    this.mmap = new MonitorMap();
    
    if (nextDump != null) {
      MutableTreeNode tmpDump = nextDump;
      nextDump = null;
      return (tmpDump);
    }
    boolean retry = false;
    String line = null;
    String tempLine = null;

    do {
      DefaultMutableTreeNode threadDump = null;
      ThreadDumpInfo overallTDI = null;
      DefaultMutableTreeNode catMonitors = null;
      DefaultMutableTreeNode catMonitorsLocks = null;
      DefaultMutableTreeNode catThreads = null;
      DefaultMutableTreeNode catLocking = null;
      DefaultMutableTreeNode catBlockingMonitors = null;
      DefaultMutableTreeNode catSleeping = null;
      DefaultMutableTreeNode catWaiting = null;

      try {
        Map threads = new HashMap();
        overallTDI = new ThreadDumpInfo("Dump No. " + counter++, 0);
        if (withCurrentTimeStamp) {
          overallTDI.setStartTime((new Date(System.currentTimeMillis())).toString());
        }
        overallTDI.setJvmVersion(this.getJvmVersion());
        
        threadDump = new DefaultMutableTreeNode(overallTDI);

        catThreads = new DefaultMutableTreeNode(new TableCategory("Threads", IconFactory.THREADS));
        threadDump.add(catThreads);

        catWaiting = new DefaultMutableTreeNode(new TableCategory("Threads waiting for Monitors",
            IconFactory.THREADS_WAITING));

        catSleeping = new DefaultMutableTreeNode(new TableCategory("Threads sleeping on Monitors",
            IconFactory.THREADS_SLEEPING));

        catLocking = new DefaultMutableTreeNode(new TableCategory("Threads locking Monitors",
            IconFactory.THREADS_LOCKING));

        // create category for monitors with disabled filtering.
        // NOTE: These strings are "magic" in that the methods
        // ThreadLogic#displayCategory and TreeCategory#getCatComponent both
        // checks these literal strings and the behavior differs.
        catMonitors = new DefaultMutableTreeNode(new TreeCategory("Monitors", IconFactory.MONITORS, false));
        catMonitorsLocks = new DefaultMutableTreeNode(new TreeCategory("Monitors without locking thread",
            IconFactory.MONITORS_NOLOCKS, false));
        catBlockingMonitors = new DefaultMutableTreeNode(new TreeCategory("Threads blocked by Monitors",
            IconFactory.THREADS_LOCKING, false));

        String title = null;
        String dumpKey = null;
        StringBuffer content = null;
        boolean inLocking = false;
        boolean inSleeping = false;
        boolean inWaiting = false;
        int threadCount = 0;
        int waiting = 0;
        int locking = 0;
        int sleeping = 0;
        boolean locked = true;
        boolean finished = false;
        Stack monitorStack = new Stack();
        long startTime = 0;
        int singleLineCounter = 0;
        boolean concurrentSyncsFlag = false;
        Matcher matched = getDm().getLastMatch();
        String parsedStartTime = null;
        
        boolean stillInParsingTitle = false;
        boolean stillInParsingStackEntry = false;
        StringBuffer titleBuffer = null;

        // Default to Hotspot unless we find any jrockit or ibm tags..        
        boolean determinedJVMType = (this.jvmType != UNKNOWN_VM);
    
        while (getBis().ready() && !finished) {
          line = getNextLine();
          lineCounter++;
          singleLineCounter++;
          
          // If there are no markers and using Fallback Parser,
          // then dont waste the lines searching for markers
          
          // Similarly, once we have started hitting thread labels( lineChecker.getStackStart(line) is not null),  
          // continue parsing, dont waste in trying to read date.... 
          if (locked && !hasStartedParsingThreads(threadCount) 
                    && lineChecker.getStackStart(line) == null) {            
            
            if (lineChecker.getFullDump(line) != null ) {
              locked = false;
              if (!withCurrentTimeStamp) {
                overallTDI.setLogLine(lineCounter);

                if (startTime != 0) {
                  startTime = 0;
                } else if (matched != null && matched.matches()) {
                  
                  parsedStartTime = ((matched.groupCount() == 1)? matched.group(1): matched.group(0));
                  
                  if (!getDm().isDefaultMatches() && isMillisTimeStamp()) {
                    try {
                      // the factor is a hack for a bug in
                      // oc4j timestamp printing (pattern
                      // timeStamp=2342342340)
                      if (parsedStartTime.length() < 13) {
                        startTime = Long.parseLong(parsedStartTime)
                            * (long) Math.pow(10, 13 - parsedStartTime.length());
                      } else {
                        startTime = Long.parseLong(parsedStartTime);
                      }
                    } catch (NumberFormatException nfe) {
                      nfe.printStackTrace();
                      startTime = 0;
                    }
                    if (startTime > 0) {
                      overallTDI.setStartTime((new Date(startTime)).toString());
                    }
                  } else {
                    overallTDI.setStartTime(parsedStartTime);
                  }
                  parsedStartTime = null;
                  matched = null;
                  getDm().resetLastMatch();
                }
              }
              dumpKey = overallTDI.getName();
            } else if (!getDm().isPatternError() && (getDm().getRegexPattern() != null)) {
              Matcher m = getDm().checkForDateMatch(line);
              if (m != null) {
                matched = m;
              }
            }
          } else {            
            // Problem with JRockit is the Timestamp occurs after the FULL THREAD DUMP tag
            // So the above logic fails as we wont get to parse for the date as its reverse for Hotspot (time occurs before Full Thread Dump marker)
            // So parse the timestamp here for jrockit....
            if ( !hasStartedParsingThreads(threadCount) && (parsedStartTime == null)  
                    && !getDm().isPatternError() && (getDm().getRegexPattern() != null)) {
              Matcher m = getDm().checkForDateMatch(line);
              if (m != null) {                
                 parsedStartTime = ((m.groupCount() == 1)? m.group(1): m.group(0));
                overallTDI.setStartTime(parsedStartTime);                
              }
            }
            
            if (line.length() == 0 || line.trim().length() == 0)
              continue;
              
            if (!determinedJVMType) {
              if (line.indexOf(JROCKIT_TAG) >= 0) {                
                this.setJvmVendor(JVM_VENDOR_LIST[JROCKIT_VM]);
                determinedJVMType = true;
              } else if (line.indexOf(IBM_TAG) >= 0) {
                this.setJvmVendor(JVM_VENDOR_LIST[IBM_VM]);
                determinedJVMType = true;
              }
            }
            
            if (((tempLine = lineChecker.getStackStart(line)) != null) || stillInParsingTitle) {              
              
              // SABHA - Commenting off the GC thread portion, we want to know
              // how many GC threads are in the jvm.. so we can provide relevant
              // advisory.
              /*
               * if (lineChecker.getGCThread(line) != null) { // skip GC Threads
               * continue; }
               */

              // We are starting a group of lines for a different
              // thread
              // First, flush state for the previous thread (if
              // any)
              concurrentSyncsFlag = false;
              
              if (!stillInParsingTitle) {
                String stringContent = content != null ? content.toString() : null;
                if (title != null) {
                  threads.put(title, content.toString());
                  content.append("</pre></pre>");
                  addToCategory(catThreads, overallTDI, title, null, stringContent, singleLineCounter, true);
                  threadCount++;
                }
                if (inWaiting) {
                  addToCategory(catWaiting, overallTDI, title, null, stringContent, singleLineCounter, true);
                  inWaiting = false;
                  waiting++;
                }
                if (inSleeping) {
                  addToCategory(catSleeping, overallTDI, title, null, stringContent, singleLineCounter, true);
                  inSleeping = false;
                  sleeping++;
                }
                if (inLocking) {
                  addToCategory(catLocking, overallTDI, title, null, stringContent, singleLineCounter, true);
                  inLocking = false;
                  locking++;
                }
                singleLineCounter = 0;
                while (!monitorStack.empty()) {
                  mmap.parseAndAddThread((String) monitorStack.pop(), title, content.toString());
                }

                // Second, initialize state for this new thread
                titleBuffer = new StringBuffer();
                content = new StringBuffer("<pre><font size=" + ThreadLogic.getFontSizeModifier(-1)
                    + ">");
              }
              
              if (tempLine == null)
                tempLine = line.trim();
              
              String useFiller = "";
              int len = titleBuffer.length();
              if (len > 0) {
                char endChar = titleBuffer.charAt(len - 1);
                char beginChar = tempLine.charAt(0);
                boolean prevEndWasNumeric = (endChar >= '0' && endChar <= '9');
                boolean newStartWasNumeric = (beginChar >= '0' && beginChar <= '9');
                useFiller = (prevEndWasNumeric && !newStartWasNumeric)?" ":"";                
              }
              
              titleBuffer.append(useFiller + tempLine.replace("\\n*", ""));
              
              if (content == null) {
                content = new StringBuffer("<pre><font size=" + ThreadLogic.getFontSizeModifier(-1)
                    + ">");
              }
              content.append(tempLine);
              content.append("\n");              
              
              // If we are still in title parsing, check if the thread label has ended...
              // Otherwise continue to treat as still in title parsing                  
              stillInParsingTitle = ( lineChecker.getEndOfTitlePattern(line) == null);              
                      
              if ((titleBuffer != null) && !stillInParsingTitle) {
                title = titleBuffer.toString();
                titleBuffer = null;
              }              
              
            } else {
              
              if (!hasStartedParsingThreads(threadCount) && content == null)
                continue;
              
              if ((tempLine = lineChecker.getThreadState(line)) != null) {
              
                content.append(tempLine);
                content.append("\n");
                if (title.indexOf("t@") > 0) {
                  // in this case the title line is missing state
                  // informations
                  String state = tempLine.substring(tempLine.indexOf(':') + 1).trim();
                  if (state.indexOf(' ') > 0) {
                    title += " nid=none " + state.substring(0, state.indexOf(' '));
                  } else {
                    title += " nid=none " + state;
                  }
                }
              //} else if (content != null && (tempLine = lineChecker.getLockedOwnable(line)) != null) {
              //  concurrentSyncsFlag = true;
              //  content.append(tempLine);
              //  content.append("\n");
              } else if (content != null && (tempLine = lineChecker.getWaitingOn(line)) != null) {
                content.append(linkifyMonitor(tempLine));
                monitorStack.push(tempLine);
                inSleeping = true;
                content.append("\n");
              } else if (content != null && (tempLine = lineChecker.getParkingToWait(line)) != null) {
                content.append(linkifyMonitor(tempLine));
                monitorStack.push(tempLine);
                inSleeping = true;
                content.append("\n");
              } else if (content != null && (tempLine = lineChecker.getWaitingTo(line)) != null) {
                content.append(linkifyMonitor(tempLine));
                monitorStack.push(tempLine);
                inWaiting = true;
                content.append("\n");
              } else if (content != null && (tempLine = lineChecker.getLocked(line)) != null) {
                content.append(linkifyMonitor(tempLine));
                inLocking = true;
                monitorStack.push(tempLine);
                content.append("\n");
              } else if (content != null && (tempLine = lineChecker.getAt(line)) != null) {
                content.append(tempLine);
                content.append("\n");
              }
            }
            
            /*
             * } else if (line.indexOf("- ") >= 0) { if (concurrentSyncsFlag) {
             * content.append(linkifyMonitor(line)); monitorStack.push(line); }
             * else { content.append(line); } content.append("\n"); }
             */
            // last thread reached?
            if ( hasStartedParsingThreads(threadCount) && (tempLine = lineChecker.getEndOfDump(line)) != null) {
              finished = true;              
              if ((checkForDeadlocks(threadDump)) == 0) {
                // no deadlocks found, set back original
                // position.
                getBis().reset();
              }
              
              // Eat away JRockit generated lock chains as it might interfere with parsing of next available dump
              if (line.contains("lock chain") && !readPastBlockedChains()) {                
                getBis().reset();
              }
              

              if (!checkThreadDumpStatData(overallTDI)) {
                // no statistical data found, set back original
                // position.
                getBis().reset();
              }              
              
              if (!(foundClassHistograms = checkForClassHistogram(threadDump))) {
                getBis().reset();                
              }
            } else {
              // Mark the point as we have successfuly parsed the thread
              getBis().mark(getMarkSize());
            }
          }
        }
        getBis().reset();
        // last thread
        String stringContent = content != null ? content.toString() : null;
        if (title != null) {
          threads.put(title, content.toString());
          content.append("</pre></pre>");
          addToCategory(catThreads, overallTDI, title, null, stringContent, singleLineCounter, true);
          threadCount++;
        }
        if (inWaiting) {
          addToCategory(catWaiting, overallTDI, title, null, stringContent, singleLineCounter, true);
          inWaiting = false;
          waiting++;
        }
        if (inSleeping) {
          addToCategory(catSleeping, overallTDI, title, null, stringContent, singleLineCounter, true);
          inSleeping = false;
          sleeping++;
        }
        if (inLocking) {
          addToCategory(catLocking, overallTDI, title, null, stringContent, singleLineCounter, true);
          inLocking = false;
          locking++;
        }
        singleLineCounter = 0;
        

        int monitorCount = mmap.size();

        int monitorsWithoutLocksCount = 0;
        int contendedMonitors = 0;
        int blockedThreads = 0;
        // dump monitors
        if (mmap.size() > 0) {
          int[] result = dumpMonitors(catMonitors, catMonitorsLocks, mmap);
          monitorsWithoutLocksCount = result[0];
          overallTDI.setOverallThreadsWaitingWithoutLocksCount(result[1]);

          result = dumpBlockingMonitors(catBlockingMonitors, mmap);
          contendedMonitors = result[0];
          blockedThreads = result[1];
        }

        // display nodes with stuff to display
        if (waiting > 0) {
          overallTDI.setWaitingThreads((Category) catWaiting.getUserObject());
          threadDump.add(catWaiting);
        }

        if (sleeping > 0) {
          overallTDI.setSleepingThreads((Category) catSleeping.getUserObject());
          threadDump.add(catSleeping);
        }

        if (locking > 0) {
          overallTDI.setLockingThreads((Category) catLocking.getUserObject());
          threadDump.add(catLocking);
        }

        if (monitorCount > 0) {
          overallTDI.setMonitors((Category) catMonitors.getUserObject());
          threadDump.add(catMonitors);
        }

        if (contendedMonitors > 0) {
          overallTDI.setBlockingMonitors((Category) catBlockingMonitors.getUserObject());
          threadDump.add(catBlockingMonitors);
        }

        if (monitorsWithoutLocksCount > 0) {
          overallTDI.setMonitorsWithoutLocks((Category) catMonitorsLocks.getUserObject());
          threadDump.add(catMonitorsLocks);
        }

        overallTDI.setJvmType(this.getJvmVendor());
        overallTDI.setParsedWithFBParser(true);
        
        Category unsortedThreadCategory = (Category) catThreads.getUserObject();
        Category sortedThreads = sortThreadsByHealth(unsortedThreadCategory);        
        overallTDI.setThreads(sortedThreads);

        // Create relationship between LockInfo and Threads
        overallTDI.parseLocks(this);

        // Detect Deadlocks
        overallTDI.detectDeadlock();

        // Run Thread Dump Advisory
        overallTDI.runThreadsAdvisory();

        ((Category) catThreads.getUserObject()).setName(((Category) catThreads.getUserObject()) + " (" + threadCount
            + " Threads overall)");
        ((Category) catWaiting.getUserObject()).setName(((Category) catWaiting.getUserObject()) + " (" + waiting
            + " Threads waiting)");
        ((Category) catSleeping.getUserObject()).setName(((Category) catSleeping.getUserObject()) + " (" + sleeping
            + " Threads sleeping)");
        ((Category) catLocking.getUserObject()).setName(((Category) catLocking.getUserObject()) + " (" + locking
            + " Threads locking)");
        ((Category) catMonitors.getUserObject()).setName(((Category) catMonitors.getUserObject()) + " (" + monitorCount
            + " Monitors)");
        ((Category) catBlockingMonitors.getUserObject()).setName(((Category) catBlockingMonitors.getUserObject())
            + " (" + blockedThreads + " Threads blocked by " + contendedMonitors + " Monitors)");
        ((Category) catMonitorsLocks.getUserObject()).setName(((Category) catMonitorsLocks.getUserObject()) + " ("
            + monitorsWithoutLocksCount + " Monitors)");
        // add thread dump to passed dump store.
        if ((threadCount > 0) && (dumpKey != null)) {
          threadStore.put(dumpKey.trim(), threads);
        }

        // check custom categories
        addCustomCategories(threadDump);
        addCategories(threadDump);

        return (threadCount > 0 ? threadDump : null);
      } catch (FileNotFoundException e) {
        e.printStackTrace();
      } catch (StringIndexOutOfBoundsException e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(null, "Error during parsing of a found thread dump, skipping to next one!\n"
            + "Check for possible broken dumps, sometimes, stream flushing mixes the logged data.\n"
            + "Error Message is \"" + e.getLocalizedMessage() + "\". \n"
            + (line != null ? "Last line read was \"" + line + "\". \n" : ""), "Error during Parsing Thread Dump",
            JOptionPane.ERROR_MESSAGE);
        retry = true;
      } catch (IOException e) {
        e.printStackTrace();
      }
    } while (retry);

    return (null);
  }
  
  
  
    
    protected boolean readPastBlockedChains() throws IOException {
    boolean finished = false;
    boolean found = false;
    int lineCounter = 0;   
    Matcher m = null;
    
    Pattern endOfChainOrDump = Pattern.compile(".*( lock chains|END OF THREAD DUMP).*");
    while (getBis().ready() && !finished) {
      String line = getNextLine();

      if (!found && lineCounter++ < 100 && !line.equals("") ) {
        m = endOfChainOrDump.matcher(line);
        if (m.matches()) {
          found = true;
          if (line.contains("END OF THREAD DUMP")) {
            finished = true;
            break;
          }
        }
      } else if (found) {
        
        if (line.indexOf("END OF THREAD DUMP") >= 0) {
          finished = true;
          break;
        } 
      }
    }
    
    if (finished)
      getBis().mark(getMarkSize());
    
    return finished;
  }

  private boolean hasStartedParsingThreads(int threadCount) {
    return (threadCount > 0);
  }  
 
}
