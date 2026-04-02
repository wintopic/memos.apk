//go:build android

package sqlite

import (
	"database/sql"
	"sync"

	sqlite3 "github.com/mattn/go-sqlite3"
)

const androidSQLiteDriverName = "sqlite3_memos_android"

var (
	registerSQLiteDriverOnce sync.Once
	registerSQLiteDriverErr  error
)

func registerSQLiteDriver() error {
	registerSQLiteDriverOnce.Do(func() {
		sql.Register(androidSQLiteDriverName, &sqlite3.SQLiteDriver{
			ConnectHook: func(conn *sqlite3.SQLiteConn) error {
				return conn.RegisterFunc("memos_unicode_lower", func(value any) any {
					return foldUnicodeValue(value)
				}, true)
			},
		})
	})

	return registerSQLiteDriverErr
}

func sqliteDriverName() string {
	return androidSQLiteDriverName
}

func buildSQLiteDSN(dsn string) string {
	return appendDSNQuery(dsn, "_foreign_keys=0&_busy_timeout=10000&_journal_mode=WAL")
}
