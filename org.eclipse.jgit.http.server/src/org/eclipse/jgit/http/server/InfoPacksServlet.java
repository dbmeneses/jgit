/*
 * Copyright (C) 2009-2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.server;

import static org.eclipse.jgit.http.server.ServletUtils.getRepository;
import static org.eclipse.jgit.http.server.ServletUtils.sendPlainText;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.internal.storage.file.ObjectDirectory;
import org.eclipse.jgit.internal.storage.file.Pack;
import org.eclipse.jgit.lib.ObjectDatabase;

/** Sends the current list of pack files, sorted most recent first. */
class InfoPacksServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;

	/** {@inheritDoc} */
	@Override
	public void doGet(final HttpServletRequest req,
			final HttpServletResponse rsp) throws IOException {
		sendPlainText(packList(req), req, rsp);
	}

	private static String packList(HttpServletRequest req) {
		final StringBuilder out = new StringBuilder();
		final ObjectDatabase db = getRepository(req).getObjectDatabase();
		if (db instanceof ObjectDirectory) {
			for (Pack pack : ((ObjectDirectory) db).getPacks()) {
				out.append("P ");
				out.append(pack.getPackFile().getName());
				out.append('\n');
			}
		}
		out.append('\n');
		return out.toString();
	}
}
