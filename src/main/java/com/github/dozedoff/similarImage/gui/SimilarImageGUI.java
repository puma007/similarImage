/*  Copyright (C) 2013  Nicholas Wright
    
    This file is part of similarImage - A similar image finder using pHash
    
    mmut is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.github.dozedoff.similarImage.gui;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import com.github.dozedoff.similarImage.app.SimilarImage;

public class SimilarImageGUI extends JFrame {
	private static final long serialVersionUID = 1L;
	private SimilarImage parent;
	
	private JTextField path;
	private JButton find;
	
	public SimilarImageGUI(SimilarImage parent) {
		this.parent = parent;
		this.setDefaultCloseOperation(EXIT_ON_CLOSE);
		this.setSize(500, 500);
		this.setLayout(new MigLayout("wrap 1"));
		setupComponents();
		this.setVisible(true);
	}
	
	private void setupComponents() {
		path = new JTextField(20);
		find = new JButton("Find");
		
		find.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				String userpath = path.getText();
				parent.compareImages(userpath);
			}
		});
		
		this.add(path);
		this.add(find);
	}
}