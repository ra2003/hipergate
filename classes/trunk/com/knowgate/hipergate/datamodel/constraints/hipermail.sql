ALTER TABLE k_mime_msgs  ADD CONSTRAINT f2_mime_msgs  FOREIGN KEY(gu_category) REFERENCES k_categories(gu_category);
ALTER TABLE k_mime_msgs  ADD CONSTRAINT f1_mime_msgs  FOREIGN KEY(gu_workarea) REFERENCES k_workareas(gu_workarea);

ALTER TABLE k_mime_parts ADD CONSTRAINT f1_mime_parts FOREIGN KEY(gu_mimemsg)  REFERENCES k_mime_msgs(gu_mimemsg);

ALTER TABLE k_inet_addrs ADD CONSTRAINT f1_inet_addrs FOREIGN KEY(gu_mimemsg)  REFERENCES k_mime_msgs(gu_mimemsg);
ALTER TABLE k_inet_addrs ADD CONSTRAINT f2_inet_addrs FOREIGN KEY(gu_user)     REFERENCES k_users    (gu_user);
ALTER TABLE k_inet_addrs ADD CONSTRAINT f3_inet_addrs FOREIGN KEY(gu_company)  REFERENCES k_companies(gu_company);
ALTER TABLE k_inet_addrs ADD CONSTRAINT f4_inet_addrs FOREIGN KEY(gu_contact)  REFERENCES k_contacts (gu_contact);




