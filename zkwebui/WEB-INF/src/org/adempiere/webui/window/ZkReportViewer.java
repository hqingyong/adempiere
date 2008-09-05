/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 2007 Adempiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 *
 * Copyright (C) 2007 Low Heng Sin hengsin@avantz.com
 * _____________________________________________
 *****************************************************************************/
package org.adempiere.webui.window;

import java.awt.Point;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Level;

import org.adempiere.pdf.Document;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListItem;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Window;
import org.adempiere.webui.panel.StatusBarPanel;
import org.adempiere.webui.session.SessionManager;
import org.compiere.model.GridField;
import org.compiere.model.MArchive;
import org.compiere.model.MClient;
import org.compiere.model.MQuery;
import org.compiere.model.MRole;
import org.compiere.model.MUser;
import org.compiere.print.AReport;
import org.compiere.print.MPrintFormat;
import org.compiere.print.ReportEngine;
import org.compiere.print.View;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.zkoss.util.media.AMedia;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.MouseEvent;
import org.zkoss.zul.Div;
import org.zkoss.zul.Filedownload;
import org.zkoss.zul.Hbox;
import org.zkoss.zul.Iframe;
import org.zkoss.zul.Listitem;
import org.zkoss.zul.Menuitem;
import org.zkoss.zul.Menupopup;
import org.zkoss.zul.Separator;
import org.zkoss.zul.Toolbar;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Vbox;


/**
 *	Print View Frame
 *
 * 	@author 	Jorg Janke
 * 	@version 	$Id: Viewer.java,v 1.2 2006/07/30 00:51:28 jjanke Exp $
 * globalqss: integrate phib contribution from 
 *   http://sourceforge.net/tracker/index.php?func=detail&aid=1566335&group_id=176962&atid=879334
 * globalqss: integrate Teo Sarca bug fixing
 * Colin Rooney 2007/03/20 RFE#1670185 & BUG#1684142
 *                         Extend security to Info queries
 *
 * @author Teo Sarca, SC ARHIPAC SERVICE SRL
 * 				<li>FR [ 1762466 ] Add "Window" menu to report viewer.
 * 				<li>FR [ 1894640 ] Report Engine: Excel Export support
 * 
 * @author Low Heng Sin
 */
public class ZkReportViewer extends Window implements EventListener {

	private static final long serialVersionUID = 1L;
	
	/** Window No					*/
	private int                 m_WindowNo;
	/**	Print Context				*/
	private Properties			m_ctx;
	/** View Pane					*/
	private View 				m_viewPanel;
	/**	Setting Values				*/
	private boolean				m_setting = false;
	/**	Report Engine				*/
	private ReportEngine 		m_reportEngine;
	/** Drill Down/Across			*/
	private boolean				m_drillDown = true;
	/** Table ID					*/
	private int					m_AD_Table_ID = 0;
	private boolean				m_isCanExport;
	
	private MQuery 		m_ddQ = null;
	private MQuery 		m_daQ = null;
	private Menuitem 	m_ddM = null;
	private Menuitem 	m_daM = null;

	/**	Logger			*/
	private static CLogger log = CLogger.getCLogger(ZkReportViewer.class);

	//
	private StatusBarPanel statusBar = new StatusBarPanel();
	private Toolbar toolBar = new Toolbar();
	private Toolbarbutton bSendMail = new Toolbarbutton();
	private Toolbarbutton bArchive = new Toolbarbutton();
	private Toolbarbutton bCustomize = new Toolbarbutton();
	private Toolbarbutton bFind = new Toolbarbutton();
	private Toolbarbutton bExport = new Toolbarbutton();
	private Listbox comboReport = new Listbox();
	private Label labelDrill = new Label();
	private Listbox comboDrill = new Listbox();
	
	private Toolbarbutton bRefresh = new Toolbarbutton();
	private Iframe iframe;
	
	private Window winExportFile = null;
	private ConfirmPanel confirmPanel = new ConfirmPanel(true);
	private Listbox cboType = new Listbox();
	
	/**
	 * 	Static Layout
	 * 	@throws Exception
	 */
	public ZkReportViewer(ReportEngine re, String title) {		
		super();
		
		log.info("");
		m_reportEngine = re;
		m_AD_Table_ID = re.getPrintFormat().getAD_Table_ID();
		if (!MRole.getDefault().isCanReport(m_AD_Table_ID))
		{
			FDialog.error(m_WindowNo, this, "AccessCannotReport", m_reportEngine.getName());
			this.onClose();
		}
		m_isCanExport = MRole.getDefault().isCanExport(m_AD_Table_ID);
		try
		{
			m_viewPanel = re.getView();
			m_ctx = m_reportEngine.getCtx();
			jbInit();
			dynInit();
			if (!m_viewPanel.isArchivable())
				log.warning("Cannot archive Document");
		}
		catch(Exception e)
		{
			log.log(Level.SEVERE, "", e);
			FDialog.error(m_WindowNo, this, "LoadError", e.getLocalizedMessage());
			this.onClose();
		}
	}
	
	private void jbInit() {
		Grid grid = new Grid();
		grid.setWidth("100%");
		Rows rows = new Rows();
		Row row = new Row();
		
		toolBar.setHeight("26px");
		
		labelDrill.setValue(Msg.getMsg(m_ctx, "Drill") + ": ");
		toolBar.appendChild(labelDrill);
		
		comboDrill.setMold("select");
		comboDrill.setTooltiptext(Msg.getMsg(m_ctx, "Drill"));
		toolBar.appendChild(comboDrill);
		
		toolBar.appendChild(new Separator("vertical"));
		
		comboReport.setMold("select");
		comboReport.setTooltiptext(Msg.translate(m_ctx, "AD_PrintFormat_ID"));
		toolBar.appendChild(comboReport);
		
		bCustomize.setImage("/images/Preference24.png");
		bCustomize.setTooltiptext("Customize Report");
		toolBar.appendChild(bCustomize);
		bCustomize.addEventListener(Events.ON_CLICK, this);
		
		bFind.setImage("/images/Find24.png");
		bFind.setTooltiptext("Lookup Record");
		toolBar.appendChild(bFind);
		bFind.addEventListener(Events.ON_CLICK, this);
		
		toolBar.appendChild(new Separator("vertical"));
		
		bSendMail.setImage("/images/SendMail24.png");
		bSendMail.setTooltiptext("Send Mail");
		toolBar.appendChild(bSendMail);
		bSendMail.addEventListener(Events.ON_CLICK, this);
		
		bArchive.setImage("/images/Archive24.png");
		bArchive.setTooltiptext("Archived Documents/Reports");
		toolBar.appendChild(bArchive);
		bArchive.addEventListener(Events.ON_CLICK, this);
		
		if (m_isCanExport)
		{
			bExport.setImage("/images/ExportX24.png");
			bExport.setTooltiptext("Export");
			toolBar.appendChild(bExport);
			bExport.addEventListener(Events.ON_CLICK, this);
		}
		
		toolBar.appendChild(new Separator("vertical"));
		
		bRefresh.setImage("/images/Refresh24.png");
		bRefresh.setTooltiptext("Refresh");
		toolBar.appendChild(bRefresh);
		bRefresh.addEventListener(Events.ON_CLICK, this);
		
		row.appendChild(toolBar);
		rows.appendChild(row);
		
		row = new Row();
		iframe = new Iframe();
		iframe.setId("reportFrame");
		int height = Double.valueOf(SessionManager.getAppDesktop().getClientInfo().desktopHeight * 0.85).intValue();
		height = height - 30;
		iframe.setHeight(height + "px");
		iframe.setWidth("100%");
		AMedia media = new AMedia(getTitle(), "pdf", "application/pdf", m_reportEngine.createPDFData());
		iframe.setContent(media);
		iframe.setAutohide(true);
		iframe.addEventListener(Events.ON_CLICK, this);
		iframe.addEventListener(Events.ON_RIGHT_CLICK, this);
		row.appendChild(iframe);
		rows.appendChild(row);
		
		grid.appendChild(rows);
		this.appendChild(grid);
		
		this.setBorder("normal");
	}

	/**
	 * 	Dynamic Init
	 */
	private void dynInit()
	{
		fillComboReport(m_reportEngine.getPrintFormat().get_ID());

		//	fill Drill Options (Name, TableName)
		comboDrill.appendItem("", null);
		String sql = "SELECT t.AD_Table_ID, t.TableName, e.PrintName, NULLIF(e.PO_PrintName,e.PrintName) "
			+ "FROM AD_Column c "
			+ " INNER JOIN AD_Column used ON (c.ColumnName=used.ColumnName)"
			+ " INNER JOIN AD_Table t ON (used.AD_Table_ID=t.AD_Table_ID AND t.IsView='N' AND t.AD_Table_ID <> c.AD_Table_ID)"
			+ " INNER JOIN AD_Column cKey ON (t.AD_Table_ID=cKey.AD_Table_ID AND cKey.IsKey='Y')"
			+ " INNER JOIN AD_Element e ON (cKey.ColumnName=e.ColumnName) "
			+ "WHERE c.AD_Table_ID=? AND c.IsKey='Y' "
			+ "ORDER BY 3";
		boolean trl = !Env.isBaseLanguage(Env.getCtx(), "AD_Element");
		if (trl)
			sql = "SELECT t.AD_Table_ID, t.TableName, et.PrintName, NULLIF(et.PO_PrintName,et.PrintName) "
				+ "FROM AD_Column c"
				+ " INNER JOIN AD_Column used ON (c.ColumnName=used.ColumnName)"
				+ " INNER JOIN AD_Table t ON (used.AD_Table_ID=t.AD_Table_ID AND t.IsView='N' AND t.AD_Table_ID <> c.AD_Table_ID)"
				+ " INNER JOIN AD_Column cKey ON (t.AD_Table_ID=cKey.AD_Table_ID AND cKey.IsKey='Y')"
				+ " INNER JOIN AD_Element e ON (cKey.ColumnName=e.ColumnName)"
				+ " INNER JOIN AD_Element_Trl et ON (e.AD_Element_ID=et.AD_Element_ID) "
				+ "WHERE c.AD_Table_ID=? AND c.IsKey='Y'"
				+ " AND et.AD_Language=? "
				+ "ORDER BY 3";
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, m_reportEngine.getPrintFormat().getAD_Table_ID());
			if (trl)
				pstmt.setString(2, Env.getAD_Language(Env.getCtx()));
			ResultSet rs = pstmt.executeQuery();
			while (rs.next())
			{
				String tableName = rs.getString(2);
				String name = rs.getString(3);
				String poName = rs.getString(4);
				if (poName != null)
					name += "/" + poName;
				comboDrill.appendItem(name, tableName);
			}
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		if (comboDrill.getItemCount() == 1)
		{
			labelDrill.setVisible(false);
			comboDrill.setVisible(false);
		}
		else
			comboDrill.addEventListener(Events.ON_SELECT, this);

		revalidate();
	}	//	dynInit
	
	/**
	 * 	Fill ComboBox comboReport (report options)
	 *  @param AD_PrintFormat_ID item to be selected
	 */
	private void fillComboReport(int AD_PrintFormat_ID)
	{
		comboReport.removeEventListener(Events.ON_SELECT, this);
		comboReport.getItems().clear();
		KeyNamePair selectValue = null;
		//	fill Report Options
		String sql = MRole.getDefault().addAccessSQL(
			"SELECT AD_PrintFormat_ID, Name, Description "
				+ "FROM AD_PrintFormat "
				+ "WHERE AD_Table_ID=? "
				//Added Lines by Armen
				+ "AND IsActive='Y' "
				//End of Added Lines
				+ "ORDER BY Name",
			"AD_PrintFormat", MRole.SQL_NOTQUALIFIED, MRole.SQL_RO);
		int AD_Table_ID = m_reportEngine.getPrintFormat().getAD_Table_ID();
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, AD_Table_ID);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next())
			{
				KeyNamePair pp = new KeyNamePair(rs.getInt(1), rs.getString(2));
				Listitem li = comboReport.appendItem(pp.getName(), pp.getKey());
				if (rs.getInt(1) == AD_PrintFormat_ID)
				{
					selectValue = pp;
					if(selectValue != null)
						comboReport.setSelectedItem(li);
				}
			}
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}
		StringBuffer sb = new StringBuffer("** ").append(Msg.getMsg(m_ctx, "NewReport")).append(" **");
		KeyNamePair pp = new KeyNamePair(-1, sb.toString());
		comboReport.appendItem(pp.getName(), pp.getKey());
		comboReport.addEventListener(Events.ON_SELECT, this);
	}	//	fillComboReport

	/**
	 * 	Revalidate settings after change of environment
	 */
	private void revalidate()
	{
		//	Report Info
		setTitle(Msg.getMsg(m_ctx, "Report") + ": " + m_reportEngine.getName() + "  " + Env.getHeader(m_ctx, 0));
		StringBuffer sb = new StringBuffer ();
		sb.append(m_viewPanel.getPaper().toString(m_ctx))
			.append(" - ").append(Msg.getMsg(m_ctx, "DataCols")).append("=")
			.append(m_reportEngine.getColumnCount())
			.append(", ").append(Msg.getMsg(m_ctx, "DataRows")).append("=")
			.append(m_reportEngine.getRowCount());
		statusBar.setStatusLine(sb.toString());
		//
	}	//	revalidate

	/**
	 * 	Dispose
	 */
	public void onClose()
	{
		Env.clearWinContext(m_WindowNo);
		m_reportEngine = null;
		m_viewPanel = null;
		m_ctx = null;
		super.onClose();
	}	//	dispose

	public void onEvent(Event event) throws Exception {
		
		if(event.getTarget().getId().equals(ConfirmPanel.A_CANCEL))
			winExportFile.onClose();
		else if(event.getTarget().getId().equals(ConfirmPanel.A_OK))			
			exportFile();
		else if(event.getName().equals(Events.ON_CLICK) || event.getName().equals(Events.ON_SELECT)) 
			actionPerformed(event);
		else if(event.getName().equals(Events.ON_RIGHT_CLICK))
			mouse_clicked(event, true);
	}

	/**************************************************************************
	 * 	Action Listener
	 * 	@param e event
	 */
	public void actionPerformed (Event e)
	{
		if (m_setting)
			return;
		if (e.getTarget() == comboReport)
			cmd_report();
		else if (e.getTarget() == comboDrill)
			cmd_drill();
		else if (e.getTarget() == bFind)
			cmd_find();
		else if (e.getTarget() == bExport)
			cmd_export();
		else if (e.getTarget() == bSendMail)
			cmd_sendMail();
		else if (e.getTarget() == bArchive)
			cmd_archive();
		else if (e.getTarget() == bCustomize)
			cmd_customize();
		else if (e.getTarget() == bRefresh)
			iframe.invalidate();
		//
		else if (e.getTarget() == m_ddM)
			cmd_window(m_ddQ);
		else if (e.getTarget() == m_daM)
			cmd_window(m_daQ);
		//
		else if (!e.getTarget().getId().equals("reportFrame"))
			mouse_clicked(e, false);
	}	//	actionPerformed
	
	/**************************************************************************
	 * 	(Re)Set Drill Accross Cursor
	 */
	private void cmd_drill()
	{
		m_drillDown = comboDrill.getSelectedIndex() < 1;	//	-1 or 0
	}	//	cmd_drill

	/**
	 * 	Mouse clicked
	 * 	@param e event
	 * 	@param rightClick true if right click
	 */
	private void mouse_clicked (Event e, boolean rightClick)
	{
		MouseEvent me = (MouseEvent) e;
		Point point = new Point(me.getX(), me.getY());
		
		if (rightClick)
		{
			m_ddQ = m_viewPanel.getDrillDown(point);
			m_daQ = m_viewPanel.getDrillAcross(point);
			m_ddM = null;
			m_daM = null;
			if (m_ddQ == null && m_daQ == null)
				return;
			//	Create Menu
			Menupopup pop = new Menupopup();
			if (m_ddQ != null)
			{
				m_ddM = new Menuitem(m_ddQ.getDisplayName(Env.getCtx()));
				m_ddM.setTooltiptext(m_ddQ.toString());
				m_ddM.addEventListener(Events.ON_CLICK, this);
				pop.appendChild(m_ddM);
			}
			if (m_daQ != null)
			{
				m_daM = new Menuitem(m_ddQ.getDisplayName(Env.getCtx()));
				m_daM.setTooltiptext(m_daQ.toString());
				m_daM.addEventListener(Events.ON_CLICK, this);
				pop.appendChild(m_daM);
			}
			pop.open(me.getX(), me.getY());
			return;
		}
		
		if (m_drillDown)
		{
			MQuery query = m_viewPanel.getDrillDown(point);
			if (query != null)
			{
				log.info("Drill Down: " + query.getWhereClause(true));
				executeDrill(query);
			}
		}
		else if (comboDrill.getSelectedItem() != null)
		{
			ListItem li = comboDrill.getSelectedItem();
			
			if(li.getValue() != null)
			{
				MQuery query  = m_viewPanel.getDrillAcross(point);
				if (query != null)
				{
					query.setTableName(li.getValue().toString());
					log.info("Drill Accross: " + query.getWhereClause(true));
					executeDrill(query);
				}				
			}
			
		}
		cmd_drill();	//	setCursor
	}	//	mouse_clicked

	/**
	 * 	Execute Drill to Query
	 * 	@param query query
	 */
	private void executeDrill (MQuery query)
	{
		int AD_Table_ID = AReport.getAD_Table_ID(query.getTableName());
		if (!MRole.getDefault().isCanReport(AD_Table_ID))
		{
			FDialog.error(m_WindowNo, this, "AccessCannotReport", query.getTableName());
			return;
		}
		if (AD_Table_ID != 0)
			new AReport (AD_Table_ID, null, query);
		else
			log.warning("No Table found for " + query.getWhereClause(true));
	}	//	executeDrill
	
	/**
	 * 	Open Window
	 *	@param query query
	 */
	private void cmd_window (MQuery query)
	{
		if (query == null)
			return;
		AEnv.zoom(query);
	}	//	cmd_window
	
	/**
	 * 	Send Mail
	 */
	private void cmd_sendMail()
	{
		String to = "";
		MUser from = MUser.get(Env.getCtx(), Env.getAD_User_ID(Env.getCtx()));
		String subject = m_reportEngine.getName();
		String message = "";
		File attachment = null;
		
		try
		{
			attachment = File.createTempFile("mail", ".pdf");
			m_reportEngine.getPDF(attachment);
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "", e);
		}
		
		WEMailDialog emd = new WEMailDialog (this,
			Msg.getMsg(Env.getCtx(), "SendMail"),
			from, to, subject, message, attachment);		
	}	//	cmd_sendMail

	/**
	 * 	Archive Report directly
	 */
	private void cmd_archive ()
	{
		boolean success = false;
		byte[] data = Document.getPDFAsArray(m_reportEngine.getLayout().getPageable(false));	//	No Copy
		if (data != null)
		{
			MArchive archive = new MArchive (Env.getCtx(), m_reportEngine.getPrintInfo(), null);
			archive.setBinaryData(data);
			success = archive.save();
		}
		if (success)
			FDialog.info(m_WindowNo, this, "Archived");
		else
			FDialog.error(m_WindowNo, this, "ArchiveError");
	}	//	cmd_archive

	/**
	 * 	Export
	 */
	private void cmd_export()
	{		
		log.config("");
		if (!m_isCanExport)
		{
			FDialog.error(m_WindowNo, this, "AccessCannotExport", getTitle());
			return;
		}
		
		if(winExportFile == null)
		{
			winExportFile = new Window();
			winExportFile.setTitle(Msg.getMsg(m_ctx, "Export") + ": " + getTitle());
			winExportFile.setWidth("450px");
			winExportFile.setClosable(true);
			winExportFile.setBorder("normal");
			winExportFile.setStyle("position:absolute");

			cboType.setMold("select");
			
			cboType.getItems().clear();			
			cboType.appendItem("ps" + " - " + Msg.getMsg(m_ctx, "FilePS"), "ps");
			cboType.appendItem("xml" + " - " + Msg.getMsg(m_ctx, "FileXML"), "xml");
			ListItem li = cboType.appendItem("pdf" + " - " + Msg.getMsg(m_ctx, "FilePDF"), "pdf");
			cboType.appendItem("html" + " - " + Msg.getMsg(m_ctx, "FileHTML"), "html");
			cboType.appendItem("txt" + " - " + Msg.getMsg(m_ctx, "FileTXT"), "txt");
			cboType.appendItem("ssv" + " - " + Msg.getMsg(m_ctx, "FileSSV"), "ssv");
			cboType.appendItem("csv" + " - " + Msg.getMsg(m_ctx, "FileCSV"), "csv");
			cboType.appendItem("xls" + " - " + Msg.getMsg(m_ctx, "FileXLS"), "xls");
			cboType.setSelectedItem(li);
			
			Hbox hb = new Hbox();
			Div div = new Div();
			div.setAlign("right");
			div.appendChild(new Label("Files of Type: "));
			hb.appendChild(div);
			hb.appendChild(cboType);
			cboType.setWidth("100%");

			Vbox vb = new Vbox();
			vb.setWidth("390px");
			winExportFile.appendChild(vb);
			vb.appendChild(hb);
			vb.appendChild(confirmPanel);	
			confirmPanel.addActionListener(this);
		}
		
		AEnv.showCenterScreen(winExportFile);
		
		cmd_drill();	//	setCursor
	}	//	cmd_export
		
	private void exportFile()
	{
		try
		{
			ListItem li = cboType.getSelectedItem();
			if(li == null || li.getValue() == null)
			{
				FDialog.error(m_WindowNo, winExportFile, "FileInvalidExtension");
				return;
			}
			
			String ext = li.getValue().toString();
			
			byte[] data = null;
			File inputFile = null;
									
			if (ext.equals("pdf"))
			{
				data = m_reportEngine.createPDFData();
			}
			else if (ext.equals("ps"))
			{
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				m_reportEngine.createPS(baos);
				data = baos.toByteArray();
			}
			else if (ext.equals("xml"))
			{
				StringWriter sw = new StringWriter();							
				m_reportEngine.createXML(sw);
				data = sw.getBuffer().toString().getBytes();
			}
			else if (ext.equals("csv") || ext.equals("ssv"))
			{
				StringWriter sw = new StringWriter();							
				m_reportEngine.createCSV(sw, ',', m_reportEngine.getPrintFormat().getLanguage());
				data = sw.getBuffer().toString().getBytes();
			}
			else if (ext.equals("txt"))
			{
				StringWriter sw = new StringWriter();							
				m_reportEngine.createCSV(sw, '\t', m_reportEngine.getPrintFormat().getLanguage());
				data = sw.getBuffer().toString().getBytes();							
			}
			else if (ext.equals("html") || ext.equals("htm"))
			{
				StringWriter sw = new StringWriter();							
				m_reportEngine.createHTML(sw, false, m_reportEngine.getPrintFormat().getLanguage());
				data = sw.getBuffer().toString().getBytes();	
			}
			else if (ext.equals("xls"))
			{
				inputFile = File.createTempFile("Export", ".xls");							
				m_reportEngine.createXLS(inputFile, m_reportEngine.getPrintFormat().getLanguage());
			}
			else
			{
				FDialog.error(m_WindowNo, winExportFile, "FileInvalidExtension");
				return;
			}

			winExportFile.onClose();
			AMedia media = null;
			if (data != null)
				media = new AMedia(m_reportEngine.getPrintFormat().getName() + "." + ext, null, "application/octet-stream", data);
			else
				media = new AMedia(m_reportEngine.getPrintFormat().getName() + "." + ext, null, "application/octet-stream", inputFile, true);
			Filedownload.save(media, m_reportEngine.getPrintFormat().getName() + "." + ext);
		}
		catch (Exception e)
		{
			log.log(Level.SEVERE, "Failed to export content.", e);
		}
	}
	
	/**
	 * 	Report Combo - Start other Report or create new one
	 */
	private void cmd_report()
	{
		ListItem li = comboReport.getSelectedItem();
		if(li == null || li.getValue() == null) return;
		
		Object pp = li.getValue();
		if (pp == null)
			return;
		//
		MPrintFormat pf = null;
		int AD_PrintFormat_ID = Integer.valueOf(pp.toString());

		//	create new
		if (AD_PrintFormat_ID == -1)
		{
			int AD_ReportView_ID = m_reportEngine.getPrintFormat().getAD_ReportView_ID();
			if (AD_ReportView_ID != 0)
			{
				String name = m_reportEngine.getName();
				int index = name.lastIndexOf('_');
				if (index != -1)
					name = name.substring(0,index);
				pf = MPrintFormat.createFromReportView(m_ctx, AD_ReportView_ID, name);
			}
			else
			{
				int AD_Table_ID = m_reportEngine.getPrintFormat().getAD_Table_ID();
				pf = MPrintFormat.createFromTable(m_ctx, AD_Table_ID);
			}
			if (pf != null)
				fillComboReport(pf.get_ID());
			else
				return;
		}
		else
			pf = MPrintFormat.get (Env.getCtx(), AD_PrintFormat_ID, true);
		
		//	Get Language from previous - thanks Gunther Hoppe 
		if (m_reportEngine.getPrintFormat() != null)
		{
			pf.setLanguage(m_reportEngine.getPrintFormat().getLanguage());		//	needs to be re-set - otherwise viewer will be blank
			pf.setTranslationLanguage(m_reportEngine.getPrintFormat().getLanguage());
		}
		m_reportEngine.setPrintFormat(pf);
		
		AMedia media = new AMedia(getTitle(), "pdf", "application/pdf", m_reportEngine.createPDFData());
		iframe.setContent(media);

		revalidate();

		cmd_drill();	//	setCursor
	}	//	cmd_report

	/**
	 * 	Query Report
	 */
	private void cmd_find()
	{
		int AD_Table_ID = m_reportEngine.getPrintFormat().getAD_Table_ID();
		
		String title = null; 
		String tableName = null;

		//	Get Find Tab Info
		String sql = "SELECT t.AD_Tab_ID "
			//	,w.Name, t.Name, w.IsDefault, t.SeqNo, ABS (tt.AD_Window_ID-t.AD_Window_ID)
			+ "FROM AD_Tab t"
			+ " INNER JOIN AD_Window w ON (t.AD_Window_ID=w.AD_Window_ID)"
			+ " INNER JOIN AD_Table tt ON (t.AD_Table_ID=tt.AD_Table_ID) "
			+ "WHERE tt.AD_Table_ID=? "
			+ "ORDER BY w.IsDefault DESC, t.SeqNo, ABS (tt.AD_Window_ID-t.AD_Window_ID)";
		int AD_Tab_ID = DB.getSQLValue(null, sql, AD_Table_ID);
		// ASP
		MClient client = MClient.get(Env.getCtx());
		String ASPFilter = "";
		if (client.isUseASP())
			ASPFilter =
				"     AND (   AD_Tab_ID IN ( "
				// Just ASP subscribed tabs for client "
				+ "              SELECT w.AD_Tab_ID "
				+ "                FROM ASP_Tab w, ASP_Level l, ASP_ClientLevel cl "
				+ "               WHERE w.ASP_Level_ID = l.ASP_Level_ID "
				+ "                 AND cl.AD_Client_ID = " + client.getAD_Client_ID()
				+ "                 AND cl.ASP_Level_ID = l.ASP_Level_ID "
				+ "                 AND w.IsActive = 'Y' "
				+ "                 AND l.IsActive = 'Y' "
				+ "                 AND cl.IsActive = 'Y' "
				+ "                 AND w.ASP_Status = 'S') " // Show
				+ "        OR AD_Tab_ID IN ( "
				// + show ASP exceptions for client
				+ "              SELECT AD_Tab_ID "
				+ "                FROM ASP_ClientException ce "
				+ "               WHERE ce.AD_Client_ID = " + client.getAD_Client_ID()
				+ "                 AND ce.IsActive = 'Y' "
				+ "                 AND ce.AD_Tab_ID IS NOT NULL "
				+ "                 AND ce.AD_Field_ID IS NULL "
				+ "                 AND ce.ASP_Status = 'S') " // Show
				+ "       ) "
				+ "   AND AD_Tab_ID NOT IN ( "
				// minus hide ASP exceptions for client
				+ "          SELECT AD_Tab_ID "
				+ "            FROM ASP_ClientException ce "
				+ "           WHERE ce.AD_Client_ID = " + client.getAD_Client_ID()
				+ "             AND ce.IsActive = 'Y' "
				+ "             AND ce.AD_Tab_ID IS NOT NULL "
				+ "             AND ce.AD_Field_ID IS NULL "
				+ "             AND ce.ASP_Status = 'H')"; // Hide
		//
		sql = "SELECT Name, TableName FROM AD_Tab_v WHERE AD_Tab_ID=? " + ASPFilter;
		if (!Env.isBaseLanguage(Env.getCtx(), "AD_Tab"))
			sql = "SELECT Name, TableName FROM AD_Tab_vt WHERE AD_Tab_ID=?"
				+ " AND AD_Language='" + Env.getAD_Language(Env.getCtx()) + "' " + ASPFilter;
		try
		{
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, AD_Tab_ID);
			ResultSet rs = pstmt.executeQuery();
			//
			if (rs.next())
			{
				title = rs.getString(1);				
				tableName = rs.getString(2);
			}
			//
			rs.close();
			pstmt.close();
		}
		catch (SQLException e)
		{
			log.log(Level.SEVERE, sql, e);
		}

		GridField[] findFields = null;
		if (tableName != null)
			findFields = GridField.createFields(m_ctx, m_WindowNo, 0, AD_Tab_ID);
		
		if (findFields == null)		//	No Tab for Table exists
			bFind.setVisible(false);
		else
		{
            FindWindow find = new FindWindow(m_WindowNo, title, AD_Table_ID, tableName,"", findFields, 1);
            find.setVisible(true);
            AEnv.showWindow(find);
            m_reportEngine.setQuery(find.getQuery());  
            find = null;
			revalidate();
		}
		cmd_drill();	//	setCursor
	}	//	cmd_find

	/**
	 * 	Call Customize
	 */
	private void cmd_customize()
	{
		int AD_Window_ID = 240;		//	hardcoded
		int AD_PrintFormat_ID = m_reportEngine.getPrintFormat().get_ID();
		AEnv.zoom(AD_Window_ID, MQuery.getEqualQuery("AD_PrintFormat_ID", AD_PrintFormat_ID));
	}	//	cmd_customize	
}
