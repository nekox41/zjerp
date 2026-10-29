import subprocess,json
def sql(s):
 return subprocess.check_output(['docker','exec','-i','zjerp-production-mysql-1','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql --default-character-set=utf8mb4 -uroot --batch --raw --skip-column-names "$MYSQL_DATABASE"'],input=s.encode()).decode()
for title,s in [('types','select contract_type,count(*),sum(amount) from zj_contract group by contract_type'),('schema','show create table zj_contract'),('dict',"select dict_label,dict_value from sys_dict_data where dict_type='contract_type'"),('menus',"select menu_id,parent_id,order_num,menu_name,path,component,perms from sys_menu where parent_id=0 or component like '%contract%'"),('relations',"select table_name,column_name from information_schema.columns where table_schema=database() and column_name like '%contract%'")]:
 print(title);print(sql(s))
