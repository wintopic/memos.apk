//go:build !android

package sqlite

import (
	"database/sql/driver"
	"sync"

	msqlite "modernc.org/sqlite"
)

var (
	registerSQLiteDriverOnce sync.Once
	registerSQLiteDriverErr  error
)

func registerSQLiteDriver() error {
	registerSQLiteDriverOnce.Do(func() {
		registerSQLiteDriverErr = msqlite.RegisterScalarFunction("memos_unicode_lower", 1, func(_ *msqlite.FunctionContext, args []driver.Value) (driver.Value, error) {
			if len(args) == 0 {
				return nil, nil
			}
			return foldUnicodeValue(args[0]), nil
		})
	})

	return registerSQLiteDriverErr
}

func sqliteDriverName() string {
	return "sqlite"
}

func buildSQLiteDSN(dsn string) string {
	return appendDSNQuery(dsn, "_pragma=foreign_keys(0)&_pragma=busy_timeout(10000)&_pragma=journal_mode(WAL)&_pragma=mmap_size(0)")
}
